# Eyes, Voice, and a Wrench: What Happens When AI Can See What You See

*Building FixIt Genie — a real-time multimodal AI agent for equipment repair, powered by Gemini Live API and Google ADK.*

---

## The Problem Is Bigger Than a Broken Dishwasher

There are 15 million skilled trade workers in the United States alone. Electricians, mechanics, HVAC technicians, biomedical engineers, factory maintenance techs. Every single day, they face equipment they've never seen before, error codes with no obvious cause, and procedures that require three hands and a photographic memory.

The tools they have? A PDF manual. A YouTube video that doesn't quite match their model. A colleague who might pick up the phone.

Now picture this: a maintenance technician on a factory floor, hands inside an electrical panel, Ray-Ban glasses on. An AI agent is watching through the glasses camera — seeing the exact wiring configuration, the exact components in front of them — and talking them through the fault diagnosis step by step. Hands-free. No phone to hold. No manual to flip through. Just a voice that sees what they see.

That's not a demo. That's what we built.

**People in skilled trades don't need more manuals. They need someone who can see what they're looking at and talk them through it.** Gemini Live makes that possible. And we built FixIt Genie to prove it.

---

## A New Category of AI Tool

Most AI tools today are either blind (text chat) or passive (image upload → response). What Gemini Live enables is something qualitatively different: an AI that **sees continuously, speaks naturally, and acts on domain knowledge** — all at the same time, in real time.

That combination defines a new class of tool. Not a chatbot. Not a vision API wrapper. Something that behaves more like a knowledgeable colleague standing next to you.

The pattern generalizes far beyond home repair:

- **Factory floor** — A technician wears Ray-Ban glasses. The agent sees the machine, cross-references the OEM maintenance manual, and guides the lockout/tagout procedure before any panel opens.
- **Insurance claims** — A field adjuster points their phone at storm damage. The agent reads repair guides and local building codes in real time, then generates a structured damage report on the spot.
- **Biomedical engineering** — A technician servicing hospital imaging equipment gets an agent that cross-references FDA device databases, manufacturer service manuals, and facility service history — with the same safety-first discipline that warns about radiation interlocks and high-voltage components.

**FixIt Genie is our proof of concept.** It targets equipment repair for consumers and professionals — but the architecture is the platform.

---

## What FixIt Genie Does

FixIt Genie is a native Android app powered by Gemini 2.5 Flash Native Audio through the Google Agent Development Kit (ADK). You point your camera at whatever's broken, describe the problem out loud, and the AI agent sees what you see, understands what you're saying, and walks you through diagnosis and repair in natural conversation.

**Three core capabilities:**

1. **Visual Understanding** — The agent processes camera frames at 1 FPS, identifying equipment types, reading error codes, checking gauge levels, and spotting issues you might miss.
2. **Conversational Guidance** — Bidirectional audio streaming means you talk naturally. The agent asks clarifying questions, gives one step at a time, and confirms each step visually before moving on.
3. **Safety-First Design** — Before guiding any physical action (breaker panel, hot coolant, battery terminals), the agent always retrieves and communicates relevant safety warnings. This isn't a feature — it's enforced in the system prompt.

### Ray-Ban Glasses: Hands-Free Repair

For situations where holding a phone gets in the way — under a car, inside an appliance panel, deep in an engine bay — FixIt Genie supports **Ray-Ban Meta glasses** as a fully integrated camera source. One tap switches the live video stream from your phone camera to the glasses. The same AI agent that was watching your phone is now watching through your eyewear, hands-free.

This isn't a novelty. For professional use cases — factory floor, field service, surgical suite — it's the only viable form factor. You cannot hold a phone while your hands are inside running machinery.

The UI reflects the tool's purpose: a genie avatar with ripple rings driven by audio level, a chat transcript layout for reviewing the conversation at a glance, and an onboarding flow ("See It. Say It. Fix It.") that sets expectations in three words.

---

## Architecture: How It All Fits Together

The system is a monorepo: a native Android app and a Python backend on Google Cloud Run.

### The Android App (Kotlin + Jetpack Compose)

Three real-time data streams, one WebSocket connection:

**Camera (CameraX)** — `ImageAnalysis` captures frames from the back camera at 1 FPS, scaled to 768×768, JPEG-compressed, Base64-encoded, and sent as ADK `LiveRequest` JSON blobs. When glasses mode is active, `GlassesCameraManager` replaces this stream with I420→JPEG-converted frames from the Meta DAT SDK.

**Audio Input (AudioRecord)** — Raw PCM at 16kHz mono, streamed as Base64-encoded blobs over the same connection. The native audio model handles voice activity detection automatically — no need to signal speech start/end.

**Audio Output (AudioTrack)** — The agent's voice responses arrive as PCM audio in `LiveEvent` messages. `AudioTrack` is configured with `USAGE_VOICE_COMMUNICATION` and `MODE_IN_COMMUNICATION` so hardware acoustic echo cancellation kicks in — critical when the agent's voice and the user's microphone are in the same room.

`SessionViewModel` orchestrates camera, audio, and WebSocket, exposing a `StateFlow<SessionUiState>` to the Compose UI. Hilt manages all dependencies.

### Ray-Ban Glasses Integration

The Meta DAT SDK (`mwdat-core`, `mwdat-camera`) streams glasses frames via the Wearables SDK. `GlassesCameraManager` handles I420→JPEG conversion and drops frames into the same pipeline as the phone camera. `SessionViewModel` exposes a `CameraSource` enum (`PHONE`/`GLASSES`) with a live `switchCameraSource()` toggle — no session restart required.

### The Backend (Google ADK on Cloud Run)

The backend uses ADK's `adk web`, which exposes a `/run_live` WebSocket endpoint for bidi-streaming. The agent combines three ADK domain skills with six function tools:

**Domain Skills** (`SkillToolset`) — loaded on demand:
- `automotive` — engine oil, battery/electrical, cooling system
- `electrical` — breaker panel, GFCI outlets
- `appliances` — washing machine, dishwasher, LG refrigerator

Each skill is a `SKILL.md` with behavioral instructions plus `references/` markdown docs. The agent calls `list_skills` to discover available domains and `load_skill` to pull one into context only when needed — keeping the context window lean. Skills define *how the agent behaves* in a domain; knowledge retrieval is a separate layer.

**Function Tools:**
1. `lookup_equipment_knowledge` — Semantic vector search via Firestore `find_nearest()`
2. `get_safety_warnings` — Category-specific safety warnings, called before any physical action
3. `log_diagnostic_step` — Session transcript recording
4. `google_search` — Real-time web search for error codes and model-specific procedures
5. `analyze_youtube_repair_video` — Transcript-based video summarization
6. `lookup_user_manual` — Manufacturer PDF extraction and summarization

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

Cloud Run with session affinity (critical for persistent WebSocket connections). The `deploy.sh` script is a single-command IaC deployment: enables APIs, creates the Artifact Registry, builds the container, deploys. One discovery worth noting: `gemini-2.5-flash-native-audio-preview` only works with the Gemini API, not Vertex AI. `GOOGLE_GENAI_USE_VERTEXAI=FALSE` plus a direct API key is the required configuration.

---

## The Knowledge Stack

### Why ADK Skills + Vector Search Instead of a Hardcoded Dict?

The first prototype embedded knowledge directly in `tools.py` as a Python dictionary. Fast, dependency-free, and architecturally wrong for three reasons: keyword matching fails on synonyms ("engine won't turn over" misses the battery document), adding knowledge requires redeploying code, and a hardcoded dict is a weak answer to "show me evidence of grounding."

The production architecture uses two complementary layers:

**ADK Skills** — Each skill package is a `SKILL.md` with behavioral instructions plus `references/` markdown docs. The behavioral layer (how the agent diagnoses, what questions it asks, when it escalates) is separate from the knowledge layer. Agents that mix behavior and facts in a single blob become brittle.

**Firestore Vector Search** — Equipment documents are embedded with `gemini-embedding-001` (3072 dimensions) and stored in Firestore with a COSINE vector index. `lookup_equipment_knowledge` calls `find_nearest()` — "engine oil pressure alarm" semantically matches the oil system document with zero keyword overlap. The original Python dict stays as a last-resort fallback so the tool never fails silently.

Fully Google-native: Gemini API for embeddings, Firestore for vector storage, ADK for the skill layer.

### Handling the Long Tail

Nine knowledge documents cover common scenarios well. Someone with a 2009 Mitsubishi Outlander asking about a P2101 throttle actuator code will exhaust the skills KB fast. Three web tools handle the rest — each with non-obvious implementation decisions:

**`google_search`** — `GoogleSearchTool` (ADK's built-in grounding tool) cannot coexist with custom function tools by default. ADK enforces a limit that isn't prominently documented — required reading source code to find. The fix: `bypass_multi_tools_limit=True` on the `GoogleSearchTool` instance. Without it, every live session throws a `400 INVALID_ARGUMENT` on deployment.

**`analyze_youtube_repair_video`** — The obvious approach (pass the YouTube URL to Gemini's REST API) works for massively popular videos but silently fails for niche repair channels — exactly the content that's most useful. The fix: fetch the transcript independently via `youtube-transcript-api` (works for any video with auto-generated captions), pass the text to Gemini for summarization, have the agent narrate the steps. Deterministic, fast, no dependency on Gemini's video indexing corpus.

**`lookup_user_manual`** — ManualsLib has no public API and bot-detection blocks scraping. Instead: a grounded Gemini search query finds the manufacturer's official PDF URL, `requests` fetches it, `pypdf` extracts the text, a second Gemini call summarizes the error codes and troubleshooting steps.

The three tools compose naturally: search surfaces the URL, the specialized tools extract structured content, the live agent synthesizes everything into a spoken step-by-step guide.

---

## Design Decisions That Mattered

**Why native Android instead of web?** CameraX and AudioRecord give much lower latency than WebRTC in a browser. When you're talking someone through a repair, every millisecond of lag makes the conversation feel unnatural. Native also handles background audio properly and gives access to the real camera torch — genuinely useful when peering into dark engine bays.

**Why safety-first as a core design principle?** This is a differentiator. Every tool call that could result in physical action requires a safety check before the agent gives any instruction. The agent won't tell you to "open the breaker panel" without first warning about lethal voltages. Responsible AI design isn't a compliance checkbox — for a repair guidance app, it's what makes the product trustworthy.

---

## Lessons Learned

**Bidi-streaming is the key differentiator.** Most entries will use request-response patterns. Real-time bidi-streaming with audio feels qualitatively different — it's a conversation, not a chatbot. The Gemini native audio model handles voice activity detection automatically, which makes the interaction feel natural without any engineering effort on VAD.

**1 FPS is plenty for equipment diagnosis.** Equipment doesn't move fast. One frame per second at 768×768 JPEG is enough for reading gauges, error codes, and identifying components while keeping bandwidth and cost reasonable.

**System prompts matter enormously for multimodal agents.** The difference between "identify the equipment" and "describe what you see to build trust, then identify the equipment" is the difference between a tool and an assistant. Users need to know the agent is actually seeing what they're showing it — trust comes before guidance.

**Function calling during live streaming is powerful.** The native audio model supports custom function calling alongside bidi-streaming. The user says "my washing machine shows E4" and the agent seamlessly queries the knowledge base and responds with the specific diagnostic procedure — no interruption in the conversation flow.

**Model selection matters.** Not all Gemini models support `bidiGenerateContent` with custom tools. `gemini-2.5-flash-native-audio-latest` is the right choice for live audio streaming with function calling. For text-only testing, `gemini-2.5-flash` works via the standard REST endpoint.

---

## The Pattern Generalizes

FixIt Genie targets equipment repair, but the architecture is a template. The camera and audio pipeline, WebSocket protocol, ADK skills structure, safety-first design, and Ray-Ban glasses support are all reusable infrastructure. A new vertical means new tools and a new system prompt — the streaming core stays the same.

Here's what that looks like in practice:

**Industrial maintenance** — A technician wears Ray-Ban glasses on the factory floor, hands occupied. The agent sees the machine through the glasses camera, has access to OEM maintenance manuals and the facility's error database, and guides the tech through the lockout/tagout procedure before any panel is opened. The hands-free form factor isn't optional here — it's the only way the tool is usable.

**Fleet and field service** — A truck driver broken down on the road describes the symptom. The agent knows the vehicle VIN, pulls active service bulletins for that chassis, and guides a roadside repair — escalating to "call roadside assistance" when the fix is outside safe scope. The same safety-first enforcement that protects a homeowner at a breaker panel protects a driver on a highway shoulder.

**Healthcare equipment** — A biomedical technician servicing hospital imaging equipment gets an agent that cross-references FDA device databases, manufacturer service manuals, and facility service history. The safety layer warns about radiation interlocks and high-voltage components with the same discipline it warns about electrical panels.

**Swap the knowledge tools and system prompt. The streaming core delivers the same conversational, visually-grounded experience.**

---

## Close

We started with a simple observation: the most dangerous moment in equipment repair is when someone who doesn't know what they're looking at has to make a decision. The standard solution — manuals, tutorials, hotlines — all have the same flaw. They can't see what you see.

Gemini Live changes that constraint. An AI that streams your camera, hears your voice, retrieves domain knowledge, and talks back in real time isn't a better search engine. It's a different kind of tool entirely.

FixIt Genie is one application. The category is wide open.

---

*Built for the Gemini Live Agent Challenge using Google ADK, Gemini 2.5 Flash Native Audio, Cloud Run, and a native Android app with CameraX, Ray-Ban Meta glasses support, and Jetpack Compose.*

*[GitHub Repository](https://github.com/dabra-labs/fixbuddy) | [Live Demo Page](https://fixit-genie.web.app) | [Demo Video](https://youtube.com/watch?v=REPLACE_WITH_ACTUAL_LINK)*
