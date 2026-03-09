# Building FixIt Buddy: A Multimodal AI Repair Agent with Gemini Live API and Google ADK

*How I built an Android app that sees through your camera, hears you describe the problem, and talks you through fixing equipment — in real time.*

---

## The Problem

We've all been there. Something breaks — the car won't start, a breaker trips, the washing machine throws an error code — and we're standing there, phone in hand, scrolling through YouTube videos trying to find one that matches our exact situation.

The problem with video tutorials is they're one-size-fits-all. They can't see what *you're* looking at. They can't adapt when your setup is different. And they definitely can't answer "wait, which one is the oil dipstick?" when you're staring under the hood for the first time.

What if you had a knowledgeable friend standing right there with you, looking at the same thing, guiding you step by step?

That's FixIt Buddy.

## What FixIt Buddy Does

FixIt Buddy is a native Android app powered by Google's Gemini 2.5 Flash Native Audio model through the Agent Development Kit (ADK). You point your phone camera at whatever's broken, describe the problem out loud, and the AI agent sees what you see, understands what you're describing, and walks you through the diagnosis and fix with natural voice conversation.

It's like having a mechanic, electrician, and appliance repair expert in your pocket — one who can actually see what you're looking at.

**Three core capabilities:**

1. **Visual Understanding** — The agent processes camera frames at 1 FPS, identifying equipment types, reading error codes, checking gauge levels, and spotting potential issues you might miss.

2. **Conversational Guidance** — Bidirectional audio streaming means you talk naturally. The agent asks clarifying questions, gives one step at a time, and confirms each step visually before moving on.

3. **Safety-First Design** — Before guiding any physical action (opening a breaker panel, checking hot coolant, handling batteries), the agent always retrieves and communicates relevant safety warnings.

## Architecture: How It All Fits Together

The system is a monorepo with two main components: a native Android app and a Python backend running on Google Cloud Run.

### The Android App (Kotlin + Jetpack Compose)

The app handles three real-time data streams simultaneously:

**Camera (CameraX)** — An `ImageAnalysis` use case captures frames from the back camera. Frames are throttled to 1 per second (Gemini's processing rate), scaled to 768x768, JPEG-compressed, Base64-encoded, and sent over WebSocket using the ADK LiveRequest protocol.

**Audio Input (AudioRecord)** — Raw PCM audio at 16kHz mono, streamed as Base64-encoded blobs over the same WebSocket connection. The native audio model handles voice activity detection automatically — no need to signal when the user starts or stops speaking.

**Audio Output (AudioTrack)** — The agent's voice responses come back as PCM audio in LiveEvent messages, played through an `AudioTrack` configured for the speech content type.

All three streams flow through a single OkHttp WebSocket connection to the backend. The `SessionViewModel` orchestrates the camera manager, audio manager, and WebSocket client, exposing a clean `StateFlow<SessionUiState>` to the Compose UI.

### The Backend (Google ADK on Cloud Run)

The backend uses Google's Agent Development Kit (ADK) with `adk web`, which exposes a `/run_live` WebSocket endpoint for bidi-streaming. This handles the complexity of maintaining a live conversation session with Gemini.

The agent has three custom tools:

1. **`lookup_equipment_knowledge`** — Queries an embedded knowledge base of diagnostic procedures. Seven equipment documents cover automotive, electrical, and appliance scenarios, with 33 error codes and 28 step-by-step procedures. Each procedure includes visual cues that tell the agent what to look for through the camera.

2. **`get_safety_warnings`** — Returns category-specific safety warnings (electrical, mechanical, fluid, pressure, heat, chemical). The agent *must* call this before guiding any physical action — it's enforced in the system prompt.

3. **`log_diagnostic_step`** — Records each diagnostic step for the session transcript, building a structured repair log.

```python
agent = Agent(
    model="gemini-2.5-flash-native-audio-preview-12-2025",
    name="fixitbuddy",
    instruction=SYSTEM_INSTRUCTION,
    tools=[
        lookup_equipment_knowledge,
        get_safety_warnings,
        log_diagnostic_step,
    ],
)
```

### Deployment

The backend runs as a Cloud Run service with session affinity enabled (critical for maintaining WebSocket connections across requests). The deploy script (`deploy.sh`) is a single-command IaC deployment that enables APIs, creates the Artifact Registry, builds the container, and deploys — earning the infrastructure-as-code bonus.

One key discovery: the `gemini-2.5-flash-native-audio-preview` model only works with the Gemini API (not Vertex AI). The deploy script sets `GOOGLE_GENAI_USE_VERTEXAI=FALSE` and passes the API key directly.

## Design Decisions That Mattered

### Why Native Android Instead of Web?

Three reasons:

1. **Camera and audio performance** — Native CameraX and AudioRecord give much lower latency than WebRTC in a browser. When you're talking someone through a repair, every millisecond of lag makes the conversation feel unnatural.

2. **Portability** — The whole point is being in the garage, at the breaker panel, next to the washing machine. A native app handles background audio properly, and the flashlight toggle uses the real camera torch — genuinely useful when you're peering into dark engine bays.

3. **Polished UX** — Material 3 theming with a purposeful color palette (Safety Orange primary, Tool Blue secondary) gives the app a professional, trustworthy feel. The onboarding flow ("See It. Say It. Fix It.") sets expectations clearly before the first session.

### Why an Embedded Knowledge Base?

The 7 equipment documents are embedded directly in `tools.py` as a Python dictionary. This means the agent works without any external database dependency — critical for demo reliability and fast response times. The knowledge base covers 33 error codes across automotive (P-codes), electrical, and appliance (E/F/UE/OE codes) categories, with Firestore available as an extension point for production scaling.

### Why Safety-First as a Core Design Principle?

This isn't just a nice feature — it's a differentiator. Every tool interaction that could result in physical action requires a safety check. The agent won't tell you to "open the breaker panel" without first warning about lethal voltages. This shows responsible AI design and addresses a genuine concern with repair guidance apps.

## The Knowledge Base

Seven curated documents covering real-world repair scenarios:

**Automotive** — Engine oil system (dipstick reading, oil level, oil pressure codes P0520-P0524), car battery and electrical (terminal corrosion, jump starting, alternator diagnosis, codes P0562-P0621), and cooling system (overheating, coolant levels, radiator cap safety, codes P0115-P0128).

**Electrical** — Residential breaker panel (tripped breaker identification, reset procedure, when to call an electrician) and GFCI outlets (reset procedure, downstream protection, monthly testing).

**Appliances** — Washing machine (12 error codes across brands, drain issues, unbalanced loads), dishwasher (Bosch E15 water-in-base, drain problems), and LG refrigerator (12 error codes including Er IF/FF/CF/dF, ice maker troubleshooting, compressor diagnosis, Smart Diagnosis feature).

Each document includes diagnostic steps with visual cues (what the agent should look for through the camera), common issues with root causes and fixes, error code mappings, and safety notes. The visual cues are especially important — they tell the agent what to describe when confirming it sees the right thing through the camera.

## Lessons Learned

**Bidi-streaming is the key differentiator.** Most entries will use request-response patterns. Real-time bidi-streaming with audio feels genuinely different — it's a conversation, not a chatbot. The Gemini native audio model handles voice activity detection automatically, which makes the interaction natural.

**1 FPS is plenty for equipment diagnosis.** Equipment doesn't move fast. One frame per second at 768x768 JPEG is enough for reading gauges, error codes, and identifying components, while keeping bandwidth reasonable.

**System prompts matter enormously for multimodal agents.** The difference between "identify the equipment" and "describe what you see to build trust, then identify the equipment" is the difference between a tool and an assistant. Users need to know the agent is actually seeing what they're showing it.

**Function calling during live streaming is powerful.** The native audio model supports custom function calling alongside bidi-streaming. The user says "my washing machine shows E4" and the agent seamlessly queries the knowledge base and responds with the specific diagnostic procedure — no interruption in the conversation flow.

**Model selection matters.** Not all Gemini models support bidiGenerateContent with custom tools. `gemini-2.5-flash-native-audio-preview-12-2025` is the right choice for live audio streaming with function calling. For text-only testing, `gemini-2.5-flash` works via the REST endpoint.

## What's Next

FixIt Buddy is designed as more than a hackathon entry. The same architecture handles any equipment category — industrial machinery, HVAC systems, plumbing, vehicle maintenance. The knowledge base is extensible (add documents and the agent can reference them immediately), and the Android app's MVVM architecture makes it straightforward to add features like session history, saved equipment profiles, and pro-level diagnostic workflows.

The core insight is simple: people don't need more repair manuals. They need someone who can see what they're looking at and talk them through it. Gemini Live makes that possible.

---

*FixIt Buddy was built for the Gemini Live Agent Challenge using Google ADK, Gemini 2.5 Flash Native Audio, Cloud Run, and a native Android app with CameraX and Jetpack Compose.*

*[GitHub Repository](https://github.com/dabra-labs/fixbuddy) | [Demo Video](https://youtube.com/watch?v=REPLACE_WITH_ACTUAL_LINK)*
