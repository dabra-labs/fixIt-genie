# Building FixIt Genie: A Multimodal AI Repair Agent with Gemini Live API and Google ADK

*How I built an Android app that sees through your camera, hears you describe the problem, and talks you through fixing equipment — in real time.*

---

## The Problem

We've all been there. Something breaks — the car won't start, a breaker trips, the washing machine throws an error code — and we're standing there, phone in hand, scrolling through YouTube videos trying to find one that matches our exact situation.

The problem with video tutorials is they're one-size-fits-all. They can't see what *you're* looking at. They can't adapt when your setup is different. And they definitely can't answer "wait, which one is the oil dipstick?" when you're staring under the hood for the first time.

What if you had a knowledgeable friend standing right there with you, looking at the same thing, guiding you step by step?

That's FixIt Genie.

## What FixIt Genie Does

FixIt Genie is a native Android app powered by Google's Gemini 2.5 Flash Native Audio model through the Agent Development Kit (ADK). You point your phone camera at whatever's broken, describe the problem out loud, and the AI agent sees what you see, understands what you're describing, and walks you through the diagnosis and fix with natural voice conversation.

It's like having a mechanic, electrician, and appliance repair expert in your pocket — one who can actually see what you're looking at. The genie persona has an animated canvas avatar with ripple rings driven by audio level, and a chat bubble transcript layout (user messages right-aligned, genie responses left-aligned) so you can review the conversation at a glance.

For situations where holding a phone gets in the way — under a car, inside an appliance panel, deep in an engine bay — FixIt Genie also supports **Ray-Ban Meta glasses** as an optional camera source. Switch to glasses mode with a single tap and the same AI agent that was watching your phone camera is now watching through your eyewear, hands-free.

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

The agent combines three ADK domain skills with six function tools:

**Domain Skills** (via `SkillToolset`) — loaded on demand as the conversation develops:
- `automotive` — engine oil, battery/electrical, cooling system
- `electrical` — breaker panel, GFCI outlets
- `appliances` — washing machine, dishwasher, LG refrigerator

Each skill is a `SKILL.md` with behavioral instructions plus `references/` markdown docs. The agent calls `list_skills` to discover what's available and `load_skill` to pull a domain into context only when needed — keeping the context window lean.

**Function Tools:**
1. **`lookup_equipment_knowledge`** — Semantic vector search via Firestore `find_nearest()` with `gemini-embedding-001` embeddings. Falls back to keyword matching if Firestore is unavailable.
2. **`get_safety_warnings`** — Returns category-specific safety warnings (electrical, mechanical, fluid, pressure, heat, chemical). Called before any physical action — enforced in the system prompt.
3. **`log_diagnostic_step`** — Records each diagnostic step for the session transcript.
4. **`google_search`** — Real-time web search for error codes and model-specific procedures.
5. **`analyze_youtube_repair_video`** — Fetches the video transcript and summarizes repair steps with Gemini, then narrates them verbally.
6. **`lookup_user_manual`** — Finds the official manufacturer PDF via grounded search, extracts text with `pypdf`, and summarizes error codes and troubleshooting procedures.

```python
from google.adk.skills import load_skill_from_dir
from google.adk.tools.skill_toolset import SkillToolset
from google.adk.tools.google_search_tool import GoogleSearchTool

_skill_toolset = SkillToolset(skills=[
    load_skill_from_dir(_SKILLS_DIR / "automotive"),
    load_skill_from_dir(_SKILLS_DIR / "electrical"),
    load_skill_from_dir(_SKILLS_DIR / "appliances"),
])

agent = Agent(
    model="gemini-2.5-flash-native-audio-latest",
    name="fixitgenie",
    instruction=SYSTEM_INSTRUCTION,
    tools=[
        _skill_toolset,
        lookup_equipment_knowledge,
        get_safety_warnings,
        log_diagnostic_step,
        GoogleSearchTool(bypass_multi_tools_limit=True),
        analyze_youtube_repair_video,
        lookup_user_manual,
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

### Why ADK Skills + Vector Search Instead of a Hardcoded Dict?

The initial prototype embedded equipment knowledge directly in `tools.py` as a Python dictionary — fast and dependency-free, but architecturally weak. Keyword matching fails on synonyms ("engine won't turn over" misses the battery document). Adding knowledge requires redeploying code. And a hardcoded dict is a poor answer to the hackathon judge's question: "Is there evidence of grounding?"

The production architecture uses two complementary layers:

**ADK Skills** (`SkillToolset` from `google.adk.tools.skill_toolset`) — three domain skill packages (`automotive`, `electrical`, `appliances`), each a `SKILL.md` with behavioral instructions plus `references/` markdown docs. The agent calls `list_skills` to discover available domains and `load_skill` to pull instructions into context on demand, keeping the context window lean. Skills define HOW the agent behaves in a domain; they don't replace knowledge retrieval.

**Firestore Vector Search** — the equipment documents are embedded with `gemini-embedding-001` (3072-dim) and stored in Firestore with a COSINE vector index. `lookup_equipment_knowledge` now calls `find_nearest()` — "engine oil pressure alarm" semantically matches the oil system document even without any keyword overlap. The original Python dict stays as a last-resort fallback, so the tool never fails silently.

This is fully Google-native: Gemini API for embeddings, Firestore for vector storage, ADK for the skill layer — and it cleanly separates what the agent *knows* from how it *behaves*.

### Why Safety-First as a Core Design Principle?

This isn't just a nice feature — it's a differentiator. Every tool interaction that could result in physical action requires a safety check. The agent won't tell you to "open the breaker panel" without first warning about lethal voltages. This shows responsible AI design and addresses a genuine concern with repair guidance apps.

## The Knowledge Base

Nine equipment documents across three categories, organized as ADK skill references:

**Automotive** — Engine oil system (dipstick reading, oil level, oil pressure codes P0520-P0524), car battery and electrical (terminal corrosion, jump starting, alternator diagnosis, codes P0562-P0621), and cooling system (overheating, coolant levels, radiator cap safety, codes P0115-P0128).

**Electrical** — Residential breaker panel (tripped breaker identification, reset procedure, when to call an electrician) and GFCI outlets (reset procedure, downstream protection, monthly testing).

**Appliances** — Washing machine (12 error codes across brands, drain issues, unbalanced loads), dishwasher (Bosch E15 water-in-base, drain problems), and LG refrigerator (12 error codes including Er IF/FF/CF/dF, ice maker troubleshooting, compressor diagnosis, Smart Diagnosis feature).

Each reference document includes diagnostic steps with visual cues (what the agent should look for through the camera), error code tables, common issues with root causes and fixes, and safety notes. The visual cues are especially important — they tell the agent what to describe when confirming it sees the right thing through the camera.

## Expanding Beyond the Skills KB

The nine skill reference documents cover common demo scenarios well, but equipment repair is a long tail. Someone with a 2009 Mitsubishi Outlander asking about a P2101 throttle actuator code, or a 1970s lathe throwing an unfamiliar alarm, will exhaust the skills KB quickly. The three web knowledge tools handle this long tail — but each had implementation decisions worth explaining.

**`google_search`** — One non-obvious constraint: `GoogleSearchTool` (ADK's built-in grounding tool) cannot coexist with custom function tools in the same agent by default. ADK enforces a limit that prevents mixing built-in tools with custom function tools — a restriction that isn't prominently documented and required reading ADK source code to find. The fix: `bypass_multi_tools_limit=True` on the `GoogleSearchTool` instance. Without it, the agent deployment throws a 400 INVALID_ARGUMENT on every live session.

**`analyze_youtube_repair_video`** — The natural first approach is passing the YouTube URL directly to Gemini's REST API and asking it to summarize the video. This works for massively popular, well-indexed videos (think Rick Astley), but silently fails for anything from a niche repair channel — which is exactly the content that's most useful. The reliable solution: fetch the transcript independently via `youtube-transcript-api` (works for any video with auto-generated captions), pass the text to Gemini for summarization, and have the agent narrate the steps. Deterministic, fast, no dependency on Gemini's video indexing corpus.

**`lookup_user_manual`** — ManualsLib has no public API, and scraping it is blocked by bot detection. Instead: a grounded Gemini search query finds the manufacturer's official PDF URL directly, `requests` fetches it, `pypdf` extracts the text, and a second Gemini call summarizes the error code table and relevant troubleshooting steps. The three tools compose naturally: search surfaces the YouTube tutorial or manual URL, the specialized tools extract structured content from those sources, and the live agent synthesizes everything into a spoken step-by-step guide.

## Lessons Learned

**Bidi-streaming is the key differentiator.** Most entries will use request-response patterns. Real-time bidi-streaming with audio feels genuinely different — it's a conversation, not a chatbot. The Gemini native audio model handles voice activity detection automatically, which makes the interaction natural.

**1 FPS is plenty for equipment diagnosis.** Equipment doesn't move fast. One frame per second at 768x768 JPEG is enough for reading gauges, error codes, and identifying components, while keeping bandwidth reasonable.

**System prompts matter enormously for multimodal agents.** The difference between "identify the equipment" and "describe what you see to build trust, then identify the equipment" is the difference between a tool and an assistant. Users need to know the agent is actually seeing what they're showing it.

**Function calling during live streaming is powerful.** The native audio model supports custom function calling alongside bidi-streaming. The user says "my washing machine shows E4" and the agent seamlessly queries the knowledge base and responds with the specific diagnostic procedure — no interruption in the conversation flow.

**Model selection matters.** Not all Gemini models support bidiGenerateContent with custom tools. `gemini-2.5-flash-native-audio-preview-12-2025` is the right choice for live audio streaming with function calling. For text-only testing, `gemini-2.5-flash` works via the REST endpoint.

## What's Next

FixIt Genie is designed as more than a hackathon entry. The real opportunity is that the same architecture adapts to radically different verticals with minimal changes. The camera and audio pipeline, WebSocket protocol, safety-first design, and Ray-Ban glasses support are all reusable infrastructure. A new vertical means new tools and a new system prompt — the streaming core stays the same.

Here's what that looks like in practice:

- **Industrial/manufacturing**: A technician on a factory floor wears Ray-Ban glasses — hands occupied, no phone to hold. The agent sees the machine through the glasses camera, has access to OEM maintenance manuals and the facility's error database, and guides the tech through the lockout/tagout procedure before any panel is opened. Ray-Ban glasses are a key enabler here: you cannot hold a phone while your hands are inside running machinery.

- **Insurance claims**: A field adjuster points their phone at storm damage. The agent reads repair guides, cost databases, and local building codes in real time, then generates a structured damage report — itemized, compliant, ready for the claim system. What takes hours of manual lookup becomes a guided walkthrough.

- **Healthcare equipment**: A biomedical technician servicing hospital imaging equipment gets an agent that cross-references FDA device databases, manufacturer service manuals, and the facility's service history. The same safety-first design that warns about live electrical panels warns about radiation interlocks and high-voltage components.

- **Fleet maintenance**: A truck driver broken down on the road describes the symptom. The agent knows the vehicle VIN, pulls active service bulletins for that chassis, and guides a roadside repair — escalating to "call roadside assistance" when the fix is outside safe scope.

- **Property inspection**: A home inspector walks through a property. The agent reads appliance model numbers through the camera, cross-references recall databases and warranty records, flags code violations, and builds the inspection report section by section.

The pattern is consistent: swap the knowledge tools (embed the domain's documentation, connect the relevant databases), update the system prompt for the domain's safety culture and vocabulary, and the live streaming core delivers the same conversational, visually-grounded experience.

The core insight is simple: people in skilled trades don't need more manuals. They need someone who can see what they're looking at and talk them through it. Gemini Live makes that possible — and Ray-Ban glasses make it hands-free.

---

*FixIt Genie was built for the Gemini Live Agent Challenge using Google ADK, Gemini 2.5 Flash Native Audio, Cloud Run, and a native Android app with CameraX and Jetpack Compose.*

*[GitHub Repository](https://github.com/dabra-labs/fixbuddy) | [Demo Video](https://youtube.com/watch?v=REPLACE_WITH_ACTUAL_LINK)*
