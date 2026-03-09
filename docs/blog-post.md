# Building FixIt Buddy: A Multimodal AI Repair Agent with Gemini Live API and Google ADK

*How I built an Android app that sees through your camera, hears you describe the problem, and talks you through fixing equipment — in real time.*

---

## The Problem

We've all been there. Something breaks — the car won't start, a breaker trips, the washing machine throws an error code — and we're standing there, phone in hand, scrolling through YouTube videos trying to find one that matches our exact situation.

The problem with video tutorials is they're one-size-fits-all. They can't see what *you're* looking at. They can't adapt when your setup is different. And they definitely can't answer "wait, which one is the oil dipstick?" when you're staring under the hood for the first time.

What if you had a knowledgeable friend standing right there with you, looking at the same thing, guiding you step by step?

That's FixIt Buddy.

## What FixIt Buddy Does

FixIt Buddy is a native Android app powered by Google's Gemini 2.0 Flash Live API. You point your phone camera at whatever's broken, describe the problem out loud, and the AI agent sees what you see, understands what you're describing, and walks you through the diagnosis and fix with natural voice conversation.

It's like having a mechanic, electrician, and appliance repair expert in your pocket — one who can actually see what you're looking at.

**Three core capabilities:**

1. **Visual Understanding** — The agent processes camera frames at 1 FPS, identifying equipment types, reading error codes, checking gauge levels, and spotting potential issues you might miss.

2. **Conversational Guidance** — Bidirectional audio streaming means you talk naturally. The agent asks clarifying questions, gives one step at a time, and confirms each step visually before moving on.

3. **Safety-First Design** — Before guiding any physical action (opening a breaker panel, checking hot coolant, handling batteries), the agent always retrieves and communicates relevant safety warnings.

## Architecture: How It All Fits Together

The system is a monorepo with two main components: a native Android app and a Python backend running on Google Cloud Run.

### The Android App (Kotlin + Jetpack Compose)

The app handles three real-time data streams simultaneously:

**Camera (CameraX)** — An `ImageAnalysis` use case captures frames from the back camera. Frames are throttled to 1 per second (Gemini's processing rate), scaled to 768x768, JPEG-compressed, Base64-encoded, and sent over WebSocket as JSON messages.

**Audio Input (AudioRecord)** — Raw PCM audio at 16kHz mono, streamed as binary over the same WebSocket connection. This feeds directly into Gemini's speech recognition.

**Audio Output (AudioTrack)** — The agent's voice responses come back as 24kHz PCM audio, played through an `AudioTrack` configured for the assistant use case.

All three streams flow through a single OkHttp WebSocket connection to the backend. The `SessionViewModel` orchestrates the camera manager, audio manager, and WebSocket client, exposing a clean `StateFlow<SessionUiState>` to the Compose UI.

```kotlin
// Camera frames → WebSocket → Agent
viewModelScope.launch(Dispatchers.IO) {
    cameraManager.frames.collect { frame ->
        webSocket.sendVideoFrame(frame)
    }
}

// Audio from mic → WebSocket → Agent
viewModelScope.launch(Dispatchers.IO) {
    audioManager.audioChunks.collect { chunk ->
        webSocket.sendAudioChunk(chunk)
    }
}
```

### The Backend (Google ADK on Cloud Run)

The backend uses Google's Agent Development Kit (ADK) with bidi-streaming, which handles the complexity of maintaining a live conversation session with Gemini.

The agent has four tools:

1. **`lookup_equipment_knowledge`** — Queries a curated knowledge base of diagnostic procedures. The knowledge base is embedded directly in the tools module (7 equipment documents covering automotive, electrical, and appliance scenarios) with Firestore as the primary source when available.

2. **`get_safety_warnings`** — Returns category-specific safety warnings (electrical, mechanical, fluid, pressure, heat, chemical). The agent *must* call this before guiding any physical action.

3. **`log_diagnostic_step`** — Records each diagnostic step for the session transcript.

4. **`google_search`** — Grounding tool for looking up specific model numbers, part numbers, or manufacturer-specific information not in the knowledge base.

```python
agent = Agent(
    model="gemini-2.0-flash-live-001",
    name="fixitbuddy",
    instruction=SYSTEM_INSTRUCTION,
    tools=[
        lookup_equipment_knowledge,
        get_safety_warnings,
        log_diagnostic_step,
        google_search,
    ],
)
```

### Deployment

The backend runs as a Cloud Run service with session affinity enabled (important for maintaining WebSocket connections). The deploy script (`deploy.sh`) is a single-command IaC deployment that enables APIs, creates the Artifact Registry, builds the container, and deploys — earning us the infrastructure-as-code bonus.

## Design Decisions That Mattered

### Why Native Android Instead of Web?

Three reasons:

1. **Camera and audio performance** — Native CameraX and AudioRecord give much lower latency than WebRTC in a browser. When you're talking someone through a repair, every millisecond of lag makes the conversation feel unnatural.

2. **Portability** — The whole point is being in the garage, at the breaker panel, next to the washing machine. A native app works better offline-tolerant, handles background audio properly, and the flashlight toggle uses the real camera torch.

3. **Submission format** — The hackathon accepts APK test builds, and judges may evaluate from the demo video alone. A native app demonstrates more technical depth.

### Why Embedded Knowledge Base?

The 7 equipment documents are hardcoded directly in `tools.py` as a Python dictionary, with Firestore serving as the primary source when connected. This dual approach means:

- The agent works even without a Firestore connection (great for demos and testing)
- Latency for knowledge queries is near-zero when using the embedded fallback
- The Firestore integration shows the production-ready architecture

### Why Safety-First as a Core Design Principle?

This isn't just a nice feature — it's a differentiator. Every tool interaction that could result in physical action requires a safety check. The agent won't tell you to "open the breaker panel" without first warning about lethal voltages. This shows responsible AI design, which matters to judges evaluating innovation.

## The Knowledge Base

Seven curated documents covering the three demo scenarios:

**Automotive** — Engine oil system (dipstick reading, oil level, oil pressure codes P0520-P0524), car battery and electrical (terminal corrosion, jump starting, alternator diagnosis), and cooling system (overheating, coolant levels, radiator cap safety).

**Electrical** — Residential breaker panel (tripped breaker identification, reset procedure, when to call an electrician) and GFCI outlets (reset procedure, downstream protection, monthly testing).

**Appliances** — Washing machine (12 error codes across brands, drain issues, unbalanced loads) and dishwasher (Bosch E15 water-in-base, drain problems, child lock).

Each document includes diagnostic steps with visual cues (what to look for), common issues with root causes and fixes, error code mappings, and safety notes. The visual cues are especially important — they tell the agent what to describe when confirming it sees the right thing.

## Lessons Learned

**Bidi-streaming is the key differentiator.** Most hackathon entries will use request-response patterns. Real-time bidi-streaming with video + audio feels genuinely different — it's a conversation, not a chatbot.

**1 FPS is plenty for equipment diagnosis.** Equipment doesn't move fast. One frame per second at 768x768 JPEG is enough for reading gauges, error codes, and identifying components, while keeping bandwidth reasonable.

**System prompts matter enormously for multimodal agents.** The difference between "identify the equipment" and "describe what you see to build trust, then identify the equipment" is the difference between a tool and an assistant. Users need to know the agent is actually seeing what they're showing it.

**Function calling during live streaming is powerful.** Gemini automatically executes tool calls mid-conversation. The user says "my washing machine shows E4" and the agent seamlessly queries the knowledge base and responds with the specific diagnostic procedure — no interruption in the conversation flow.

## What's Next

FixIt Buddy is designed as more than a hackathon entry. The same architecture handles any equipment category — industrial machinery, HVAC systems, plumbing, vehicle maintenance. The knowledge base is extensible (add a Firestore document, and the agent can reference it immediately), and the Android app's MVVM architecture makes it straightforward to add features like session history, saved equipment profiles, and pro-level diagnostic workflows.

The core insight is simple: people don't need more repair manuals. They need someone who can see what they're looking at and talk them through it. Gemini Live makes that possible.

---

*FixIt Buddy was built for the Gemini Live Agent Challenge using Google ADK, Gemini 2.0 Flash Live API, Cloud Run, Cloud Firestore, and a native Android app with CameraX and Jetpack Compose.*

*[GitHub Repository](https://github.com/fixitbuddy/fixitbuddy) | [Demo Video](https://youtube.com/fixitbuddy-demo)*
