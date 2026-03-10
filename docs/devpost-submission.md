# Devpost Submission — FixIt Genie

> Pre-filled template for the Gemini Live Agent Challenge submission

## Project Name
FixIt Genie

## Tagline
See. Hear. Fix. — Your AI repair genie that sees through your camera and talks you through the fix.

## Category
The Live Agent

## What It Does
FixIt Genie is a multimodal AI agent Android app that helps anyone diagnose and repair equipment in real time. Point your phone camera — or connect Ray-Ban Meta glasses for a hands-free view — at what's broken, describe the problem, and the agent sees what you see and walks you through the fix step-by-step with natural voice conversation.

It's not a chatbot. It's a real-time, bidirectional conversation — the agent interrupts when it spots something, asks clarifying questions, and confirms each step visually through the camera before moving on. The genie persona has an animated canvas avatar with ripple rings driven by audio level, and a chat bubble transcript layout (user messages right-aligned, genie responses left-aligned).

Seven curated equipment documents across three demo scenarios show generalization, plus three new knowledge tools that reach the web for anything outside the embedded KB:
- **Automotive**: Check oil levels (P0520-P0524), diagnose battery issues (P0562-P0621), assess coolant overheating (P0115-P0128)
- **Electrical**: Reset tripped breakers, troubleshoot GFCI outlets
- **Appliances**: Decode washing machine error codes (E1-E4, UE, OE), fix dishwasher drains (E15, E24), troubleshoot LG refrigerators (Er IF, Er FF, Er CF)
- **Web search**: Real-time lookup for error codes and repair guides not in the embedded KB
- **YouTube analysis**: Transcripts from repair videos summarized and narrated by the agent
- **User manual lookup**: Grounded PDF search + extraction for manufacturer service documentation

## Inspiration
We've all stood in front of broken equipment scrolling through YouTube, trying to find a video that matches our exact situation. Tutorials can't see what you're looking at. They can't adapt when your setup is different. FixIt Genie changes that — it's like having a knowledgeable friend standing right there with you, looking at the same thing.

## How We Built It
- **Android App**: Kotlin 2.3, Jetpack Compose (Material 3), CameraX 1.4.1 for 1 FPS JPEG capture, AudioRecord/AudioTrack for bidirectional 16kHz/24kHz PCM audio, OkHttp WebSocket using ADK LiveRequest protocol, Hilt DI
- **Ray-Ban Glasses**: Meta DAT SDK v0.4.0 (`mwdat-core`, `mwdat-camera`) — `GlassesCameraManager` streams I420 frames from glasses, converted to JPEG and sent over the same WebSocket pipeline; live toggle between phone and glasses camera via `CameraSource` enum in `SessionViewModel`; minSdk bumped to 31 (Android 12+)
- **Backend**: Google ADK (`adk web`) on Cloud Run, Gemini 2.5 Flash Native Audio Preview for bidi-streaming with custom function calling
- **Knowledge Architecture**: Two-layer system — ADK `SkillToolset` with 3 domain skills (automotive, electrical, appliances), each a `SKILL.md` + `references/` markdown docs loaded on demand; Firestore vector search with `gemini-embedding-001` (3072-dim COSINE) for semantic `lookup_equipment_knowledge`; embedded Python dict as last-resort fallback
- **Custom Tools (6 total)**: Equipment knowledge lookup (vector search), safety warnings system (6 hazard categories), diagnostic step logging, `google_search` (GoogleSearchTool with `bypass_multi_tools_limit=True`), `analyze_youtube_repair_video` (transcript API + Gemini summarization), `lookup_user_manual` (grounded PDF search + pypdf extraction)
- **Infrastructure**: IaC deployment via `deploy.sh` (enables APIs, creates Artifact Registry, builds container, deploys to Cloud Run with session affinity)
- **Testing**: 234 automated tests (115 backend + 109 Android unit tests + 10 glasses tests: 5 instrumented, 5 unit)

## Challenges We Ran Into
- **ADK protocol discovery**: The LiveRequest/LiveEvent WebSocket protocol for `adk web` required significant experimentation — we had to trace actual WebSocket frames to get the exact JSON structure right (Base64-encoded audio blobs, content parts format)
- **Model selection**: Not all Gemini models support bidiGenerateContent with custom function calling tools. We tested multiple models before finding that `gemini-2.5-flash-native-audio-preview-12-2025` is the correct choice for live audio streaming with tools
- **Voice Activity Detection**: The native audio model handles VAD automatically — sending explicit `activity_end` signals actually breaks the flow. Discovering this required reading Cloud Run logs carefully
- **System prompt engineering**: Making the agent actively describe what it sees (building user trust) while keeping guidance to one step at a time required many iterations
- **`bypass_multi_tools_limit` discovery**: Combining ADK's built-in `GoogleSearchTool` with custom function tools is blocked by a default ADK constraint. The fix — `bypass_multi_tools_limit=True` on the search tool — is not prominently documented and required reading ADK source code to find
- **YouTube strategy pivot**: Passing a YouTube URL directly to Gemini works for well-indexed videos but silently fails for others. The reliable solution was fetching transcripts via `youtube-transcript-api` independently of Gemini, then passing the text to Gemini for summarization — deterministic regardless of whether Gemini has seen the video
- **Meta DAT SDK MockDeviceKit crash**: The MockDeviceKit v0.4.0 has a known native crash in `libdatax_jni.so` when processing video frames. Integration tests had to be scoped to initialization and pairing state only, with frame streaming tests deferred to real hardware

## Accomplishments We're Proud Of
- **Safety-first architecture**: The agent always calls `get_safety_warnings()` before guiding any physical action — it's enforced in the system prompt and verified by unit tests
- **Real conversation, not a chatbot**: Bidi-streaming with native audio feels qualitatively different from request-response — the agent interrupts, handles "wait" and "hold on" gracefully, and adapts in real time
- **Professional polish**: Beautiful onboarding flow ("See It. Say It. Fix It."), Material 3 theming with purposeful colors (Safety Orange, Tool Blue), animated status indicators
- **ADK Skills + vector search architecture**: Domain skills (`SkillToolset`) provide behavioral context loaded on demand; Firestore vector search with `gemini-embedding-001` enables semantic retrieval — "engine oil pressure alarm" matches the right document without keyword overlap. Fully Google-native stack (Vertex AI + Firestore + ADK).
- **Ray-Ban Meta glasses integration**: Hands-free camera via Meta DAT SDK v0.4.0 — I420→JPEG pipeline, live toggle in the session UI, same WebSocket pipeline as the phone camera
- **Knowledge expansion to the open web**: Three tools (`google_search`, `analyze_youtube_repair_video`, `lookup_user_manual`) extend the agent beyond the embedded KB to handle any equipment, any error code, any model number

## What We Learned
- 1 FPS video is plenty for equipment diagnosis — equipment doesn't move fast, and lower frame rates keep bandwidth manageable
- Bidi-streaming fundamentally changes the UX — it's the difference between a tool and a companion
- System prompt engineering matters even more for multimodal agents than for text-only — "describe what you see" is the key to building user trust
- The Gemini native audio model's automatic VAD makes conversations feel remarkably natural

## What's Next

The same architecture adapts to radically different verticals with minimal changes: swap the knowledge tools, update the system prompt. The camera and audio pipeline, WebSocket protocol, safety-first design, and Ray-Ban glasses support are all reusable infrastructure.

**Vertical opportunities:**

- **Industrial/manufacturing**: Technician on a factory floor wears Ray-Ban glasses — hands occupied, no phone possible. Agent sees the machine through the glasses, accesses OEM manuals and facility error databases, guides the lockout/tagout procedure. Ray-Ban glasses are a key enabler for any professional use case where you cannot hold a phone while working on machinery.
- **Insurance claims**: Field adjuster points phone at damage — agent reads repair guides, cost databases, and building codes, generates a structured itemized damage report.
- **Healthcare equipment**: Biomedical tech servicing hospital equipment — agent cross-references FDA device databases and manufacturer service manuals, with the same safety-first architecture warning about radiation interlocks and high-voltage components.
- **Fleet maintenance**: Truck driver broken down on the road — agent knows the vehicle VIN, pulls active service bulletins for that chassis, guides roadside repair and escalates when out of safe scope.
- **Property inspection**: Home inspector walks a property — agent reads appliance model numbers via camera, cross-references recall databases, builds the inspection report section by section.

**The key insight**: new vertical = new tools + new system prompt. The ADK tool architecture makes this a configuration change, not a rewrite.

**Near-term product improvements:**
- Session history with searchable diagnostic transcripts
- Equipment profiles that remember your specific setup
- AR overlays highlighting specific components on the camera feed
- Community-contributed knowledge documents

## Built With
- Kotlin
- Jetpack Compose
- Google ADK
- Gemini 2.5 Flash Native Audio Preview
- Google Cloud Run
- CameraX
- OkHttp
- Hilt
- Python
- Material 3
- Meta DAT SDK v0.4.0
- youtube-transcript-api
- pypdf

## Try It Out
- **GitHub**: https://github.com/dabra-labs/fixbuddy
- **Demo Video**: [YouTube link — REPLACE BEFORE SUBMISSION]
- **Blog Post**: [Medium/Dev.to link — REPLACE BEFORE SUBMISSION]
- **Live Backend**: Deployed on Google Cloud Run (see deploy.sh for setup)

---

## Bonus Points Checklist
- [x] Blog post (+0.6) — Published on Medium/Dev.to
- [x] Infrastructure as Code (+0.2) — deploy.sh with full GCP setup
- [ ] GDG membership (+0.2) — Join at developers.google.com/community/gdg
