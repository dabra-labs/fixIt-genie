# Eyes, Voice, and a Wrench: Building FixIt Genie

## The Moment It Clicked

Picture this: a maintenance technician on a factory floor, hands inside an electrical panel, Ray-Ban glasses on. An AI agent is watching through the glasses camera — seeing the exact wiring configuration, the exact components — and talking them through the fault diagnosis step by step. Hands-free. No phone to hold. No manual to flip through. Just a voice that sees what they see.

That's the category we wanted to build: **an AI that doesn't just answer questions about broken things — it sees the broken thing with you.**

The insight that drove everything: people in skilled trades don't need more information. They need a knowledgeable friend who can look at the same thing they're looking at and say "yes, that's the problem, here's what to do next." YouTube tutorials can't see your specific setup. Manuals can't adapt when your situation is different. FixIt Genie can.

---

## What FixIt Genie Does

Point your phone camera at anything broken. Describe what's wrong. The agent sees what you see — gauges, error codes, corrosion, damage — and walks you through the repair with natural voice conversation.

It's not a chatbot. It's a real-time bidirectional conversation. The agent interrupts when it spots something important, asks clarifying questions when it needs more context, and confirms each step visually before moving on. It calls `get_safety_warnings()` before guiding any physical action — high voltage, hot coolant, pinch points — because the first job is to not make things worse.

**Three demo domains** with seven curated equipment documents:
- 🚗 **Automotive** — battery diagnosis, oil pressure codes (P0520–P0524), coolant overheating
- ⚡ **Electrical** — tripped breakers, GFCI outlets, panel faults
- 🏠 **Appliances** — washing machine error codes (E1–E4, UE, OE), dishwasher drains (E15, E24), LG refrigerators

**Three tools that reach the open web** for anything outside the embedded KB:
- `google_search` — real-time lookup for unknown error codes and repair guides
- `analyze_youtube_repair_video` — fetches transcript, Gemini summarizes and narrates
- `lookup_user_manual` — grounded PDF search + extraction for manufacturer docs

**Ray-Ban Meta glasses** — for when holding a phone isn't possible. One tap switches the live video stream from phone camera to glasses. Same agent, same pipeline, hands-free.

---

## Architecture

```
┌─────────────────────────────────┐
│   Android App (Kotlin)          │
│   CameraX · AudioRecord · Hilt  │
│   GlassesCameraManager          │
└──────────────┬──────────────────┘
               │ WebSocket (wss://)
               │ Video: JSON {type:"video", data:"<base64 JPEG>"}
               │ Audio: Binary PCM 16kHz mono
               ▼
┌─────────────────────────────────┐
│  Cloud Run · Google ADK         │
│  gemini-2.5-flash-native-audio  │
│  6 function calling tools       │
└──────────┬──────────────────────┘
           │
     ┌─────┴──────────┐
     ▼                ▼
┌─────────┐    ┌──────────────┐
│Firestore│    │ Google       │
│Vector KB│    │ Search /     │
│3072-dim │    │ YouTube /    │
│COSINE   │    │ PDF          │
└─────────┘    └──────────────┘
```

**The knowledge architecture** is a two-layer system. First: ADK `SkillToolset` with three domain skills — each a `SKILL.md` + `references/` markdown documents loaded on demand. Second: Firestore vector search using `gemini-embedding-001` at 3072 dimensions with COSINE similarity. "Engine oil pressure alarm" semantically matches the right document without keyword overlap. The web tools are the fallback for anything outside both layers.

**The Android app** streams three data flows over one WebSocket:
- Camera frames: CameraX at 1 FPS, 768×768, JPEG-compressed, Base64-encoded
- Audio in: AudioRecord at 16kHz mono PCM, `VOICE_COMMUNICATION` source
- Audio out: AudioTrack at 24kHz mono PCM, `USAGE_VOICE_COMMUNICATION`

When Ray-Ban glasses mode is active, `GlassesCameraManager` replaces the CameraX stream with I420→JPEG-converted frames from the Meta DAT SDK — same WebSocket pipeline, no session restart required.

---

## Challenges

**Protocol archaeology.** The ADK LiveRequest/LiveEvent WebSocket protocol is documented at a high level, but the exact JSON structure required tracing actual WebSocket frames. The `blob` field wraps audio as `{"mime_type": "audio/pcm;rate=16000", "data": "<base64>"}`. Getting this exactly right took a full day.

**The base64 bug that produced garbage audio.** ADK sends audio responses as URL-safe base64 (`_` and `-` characters). Decoding with `Base64.NO_WRAP` silently corrupted every audio chunk — the app played noise instead of speech. Fix: `Base64.decode(data, Base64.URL_SAFE)`. Simple once found; invisible until you know to look.

**VAD is automatic — don't fight it.** The native audio model detects voice activity automatically. Sending explicit `activity_end` signals breaks the conversation flow. Discovered this by reading Cloud Run logs carefully after the agent kept cutting itself off.

**`bypass_multi_tools_limit` — undocumented but critical.** Combining ADK's built-in `GoogleSearchTool` with custom function tools is blocked by a default ADK constraint. The fix (`bypass_multi_tools_limit=True`) is not in the main docs — found it by reading ADK source code.

**YouTube reliability.** Passing a YouTube URL directly to Gemini works for well-indexed videos but silently fails for others. The reliable solution: fetch transcripts via `youtube-transcript-api` independently, then pass the text to Gemini for summarization. Deterministic, regardless of whether Gemini has seen the video.

**Meta DAT SDK MockDeviceKit crash.** The MockDeviceKit v0.4.0 has a known native crash in `libdatax_jni.so` when processing video frames. Integration tests had to be scoped to initialization and pairing state — frame streaming tests deferred to real hardware.

---

## What We Learned

**1 FPS is enough.** Equipment doesn't move fast. Lower frame rates keep bandwidth manageable without sacrificing diagnosis quality.

**Bidi-streaming changes the category.** Request-response AI is a tool. Bidi-streaming with native audio is a companion. The difference is qualitative, not incremental — the agent can interrupt, handle "wait" and "hold on," and adapt in real time.

**"Describe what you see" is the key prompt.** Making the agent actively narrate what it sees through the camera — before giving any guidance — is what builds user trust. Without it, the interaction feels like a voice chatbot. With it, it feels like a knowledgeable person looking over your shoulder.

**ADK tool architecture makes verticals cheap.** New vertical = new tools + new system prompt. The camera pipeline, audio pipeline, WebSocket protocol, and safety-first design are all reusable infrastructure. Industrial maintenance, insurance claims, fleet repair, healthcare equipment — each is a configuration change, not a rewrite.

---

## What's Next

The same architecture generalizes to any domain where someone needs expert guidance while looking at a physical thing:

- **Industrial/manufacturing** — Ray-Ban glasses on the factory floor, agent guides lockout/tagout while hands are occupied inside running machinery
- **Insurance claims** — field adjuster points phone at damage, agent generates a structured itemized report
- **Fleet maintenance** — truck driver broken down on the road, agent pulls active service bulletins for that VIN and guides roadside repair
- **Healthcare equipment** — biomedical tech servicing hospital equipment, agent cross-references FDA device databases with the same safety-first architecture

The key insight: **the category isn't repair. It's AI that sees what you see, wherever you are, whatever you're doing.**

---

*Built for the Gemini Live Agent Challenge using Google ADK, Gemini 2.5 Flash Native Audio, Cloud Run, Firestore vector search, and a native Android app with CameraX, Ray-Ban Meta glasses support, and Jetpack Compose.*
