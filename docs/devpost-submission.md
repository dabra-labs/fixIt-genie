# Devpost Submission — FixIt Buddy

> Pre-filled template for the Gemini Live Agent Challenge submission

## Project Name
FixIt Buddy

## Tagline
See. Hear. Fix. — Your AI repair companion that sees through your camera and talks you through the fix.

## Category
The Live Agent

## What It Does
FixIt Buddy is a multimodal AI agent Android app that helps anyone diagnose and repair equipment in real time. Point your phone camera at what's broken, describe the problem, and the agent sees what you see and walks you through the fix step-by-step with natural voice conversation.

It's not a chatbot. It's a real-time, bidirectional conversation — the agent interrupts when it spots something, asks clarifying questions, and confirms each step visually through the camera before moving on.

Seven curated equipment documents across three demo scenarios show generalization:
- **Automotive**: Check oil levels (P0520-P0524), diagnose battery issues (P0562-P0621), assess coolant overheating (P0115-P0128)
- **Electrical**: Reset tripped breakers, troubleshoot GFCI outlets
- **Appliances**: Decode washing machine error codes (E1-E4, UE, OE), fix dishwasher drains (E15, E24), troubleshoot LG refrigerators (Er IF, Er FF, Er CF)

## Inspiration
We've all stood in front of broken equipment scrolling through YouTube, trying to find a video that matches our exact situation. Tutorials can't see what you're looking at. They can't adapt when your setup is different. FixIt Buddy changes that — it's like having a knowledgeable friend standing right there with you, looking at the same thing.

## How We Built It
- **Android App**: Kotlin 2.3, Jetpack Compose (Material 3), CameraX 1.4.1 for 1 FPS JPEG capture, AudioRecord/AudioTrack for bidirectional 16kHz/24kHz PCM audio, OkHttp WebSocket using ADK LiveRequest protocol, Hilt DI
- **Backend**: Google ADK (`adk web`) on Cloud Run, Gemini 2.5 Flash Native Audio Preview for bidi-streaming with custom function calling
- **Knowledge Base**: 7 embedded equipment documents with 33 error codes and 28 diagnostic procedures, each including visual cues for camera verification
- **Custom Tools**: Equipment knowledge lookup (keyword + error code matching), safety warnings system (6 hazard categories), diagnostic step logging
- **Infrastructure**: IaC deployment via `deploy.sh` (enables APIs, creates Artifact Registry, builds container, deploys to Cloud Run with session affinity)
- **Testing**: 224 automated tests (115 backend + 109 Android unit tests)

## Challenges We Ran Into
- **ADK protocol discovery**: The LiveRequest/LiveEvent WebSocket protocol for `adk web` required significant experimentation — we had to trace actual WebSocket frames to get the exact JSON structure right (Base64-encoded audio blobs, content parts format)
- **Model selection**: Not all Gemini models support bidiGenerateContent with custom function calling tools. We tested multiple models before finding that `gemini-2.5-flash-native-audio-preview-12-2025` is the correct choice for live audio streaming with tools
- **Voice Activity Detection**: The native audio model handles VAD automatically — sending explicit `activity_end` signals actually breaks the flow. Discovering this required reading Cloud Run logs carefully
- **System prompt engineering**: Making the agent actively describe what it sees (building user trust) while keeping guidance to one step at a time required many iterations

## Accomplishments We're Proud Of
- **Safety-first architecture**: The agent always calls `get_safety_warnings()` before guiding any physical action — it's enforced in the system prompt and verified by unit tests
- **Real conversation, not a chatbot**: Bidi-streaming with native audio feels qualitatively different from request-response — the agent interrupts, handles "wait" and "hold on" gracefully, and adapts in real time
- **Professional polish**: Beautiful onboarding flow ("See It. Say It. Fix It."), Material 3 theming with purposeful colors (Safety Orange, Tool Blue), animated status indicators
- **Comprehensive knowledge base**: 7 equipment documents with visual cues designed specifically for camera-based verification — the agent knows *what to look for*, not just what to say

## What We Learned
- 1 FPS video is plenty for equipment diagnosis — equipment doesn't move fast, and lower frame rates keep bandwidth manageable
- Bidi-streaming fundamentally changes the UX — it's the difference between a tool and a companion
- System prompt engineering matters even more for multimodal agents than for text-only — "describe what you see" is the key to building user trust
- The Gemini native audio model's automatic VAD makes conversations feel remarkably natural

## What's Next
- Expand knowledge base to cover HVAC, plumbing, industrial machinery
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

## Try It Out
- **GitHub**: https://github.com/dabra-labs/fixbuddy
- **Demo Video**: [YouTube link — REPLACE BEFORE SUBMISSION]
- **Blog Post**: [Medium/Dev.to link — REPLACE BEFORE SUBMISSION]
- **Live Backend**: https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app

---

## Bonus Points Checklist
- [x] Blog post (+0.6) — Published on Medium/Dev.to
- [x] Infrastructure as Code (+0.2) — deploy.sh with full GCP setup
- [ ] GDG membership (+0.2) — Join at developers.google.com/community/gdg
