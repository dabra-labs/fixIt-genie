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

Three demo scenarios show generalization across domains:
- **Automotive**: Check oil levels, diagnose battery issues, assess coolant
- **Electrical**: Reset tripped breakers, troubleshoot GFCI outlets
- **Appliances**: Decode washing machine error codes, fix dishwasher drains

## Inspiration
We've all stood in front of broken equipment scrolling through YouTube, trying to find a video that matches our exact situation. Tutorials can't see what you're looking at. They can't adapt when your setup is different. FixIt Buddy changes that — it's like having a knowledgeable friend standing right there with you.

## How We Built It
- **Android App**: Kotlin + Jetpack Compose, CameraX for video capture (1 FPS), AudioRecord/AudioTrack for bidi audio, OkHttp WebSocket
- **Backend**: Google ADK with bidi-streaming on Cloud Run, Gemini 2.0 Flash Live API via Vertex AI
- **Knowledge Base**: Cloud Firestore with 7 curated equipment documents (33 error codes, 28 diagnostic procedures)
- **Tools**: Equipment knowledge lookup, safety warnings system, diagnostic logging, Google Search grounding
- **Infrastructure**: IaC deployment via deploy.sh, Dockerfile for containerization

## Challenges We Ran Into
- Synchronizing three real-time data streams (video, audio in, audio out) over a single WebSocket without dropping frames or introducing lag
- Designing a system prompt that makes the agent actively describe what it sees (building user trust) while keeping guidance to one step at a time
- Balancing embedded knowledge (for demo reliability) with Firestore queries (for production extensibility)

## Accomplishments We're Proud Of
- Safety-first design — the agent always checks safety warnings before guiding any physical action
- The agent genuinely feels like a conversation, not a chatbot — it interrupts, asks questions, and confirms visually
- Same architecture scales from home repairs to industrial equipment without code changes

## What We Learned
- 1 FPS video is plenty for equipment diagnosis — equipment doesn't move fast
- Bidi-streaming changes the UX fundamentally — it's the difference between a chatbot and a companion
- System prompt engineering matters even more for multimodal agents than for text-only

## What's Next
- Expand knowledge base to cover HVAC, plumbing, industrial machinery
- Session history with searchable diagnostic transcripts
- Equipment profiles that remember your specific setup
- Community-contributed knowledge documents

## Built With
- Kotlin
- Jetpack Compose
- Google ADK
- Gemini 2.0 Flash Live API
- Vertex AI
- Cloud Run
- Cloud Firestore
- CameraX
- OkHttp
- Python

## Try It Out
- **GitHub**: [link]
- **Demo Video**: [link]
- **Blog Post**: [link]
- **APK Download**: [link]

---

## Bonus Points Checklist
- [x] Blog post (+0.6) — Published on Medium/Dev.to
- [x] Infrastructure as Code (+0.2) — deploy.sh with full GCP setup
- [ ] GDG membership (+0.2) — Join at developers.google.com/community/gdg
