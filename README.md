# FixIt Buddy

**See. Hear. Fix.** Point your phone camera at broken equipment, describe the problem, and get expert step-by-step voice guidance in real time.

Built with **Google ADK** + **Gemini Live API** for the [Gemini Live Agent Challenge](https://geminiliveagentchallenge.devpost.com/).

---

## What It Does

FixIt Buddy is a multimodal AI agent that:

1. **Sees** through your phone camera — identifies equipment, reads error codes, gauges, and labels
2. **Listens** to you describe the problem — understands context and asks clarifying questions
3. **Talks you through the fix** — step-by-step voice guidance, confirming each step visually before moving on
4. **Keeps you safe** — always checks safety warnings before guiding any physical action

### Demo Scenarios

- **Automotive**: Check oil, diagnose battery issues, assess coolant levels
- **Electrical**: Reset tripped breakers, troubleshoot GFCI outlets
- **Appliances**: Decode washing machine/dishwasher error codes, troubleshoot LG refrigerators

Same architecture works for industrial equipment, HVAC, plumbing, and more.

---

## Architecture

```
┌─────────────────────┐     WebSocket (ADK LiveRequest)     ┌──────────────────────────────┐
│   Android App       │◄───────────────────────────────────►│   Google Cloud Run            │
│                     │                                      │                              │
│  • CameraX (1 FPS)  │  Audio PCM 16kHz ──────────►        │  • Google ADK (adk web)      │
│  • AudioRecord      │  ◄────────── Audio PCM 24kHz        │  • Gemini 2.5 Flash          │
│  • Jetpack Compose  │  ◄────────── Transcripts             │    Native Audio Preview      │
│  • Material 3       │                                      │  • 3 Custom Function Tools   │
│  • OkHttp WebSocket │                                      │  • Embedded Knowledge Base   │
│  • Hilt DI          │                                      │    (7 docs, 33 error codes)  │
└─────────────────────┘                                      └──────────────────────────────┘
```

**Live deployment**: Cloud Run at `us-central1` with session affinity for persistent WebSocket connections.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android App | Kotlin 2.3, Jetpack Compose (BOM 2025.04.01), Material 3, CameraX 1.4.1, Hilt 2.59.2 |
| Backend Agent | Google ADK (`adk web`), Gemini 2.5 Flash Native Audio Preview, Python 3.12 |
| Infrastructure | Google Cloud Run (2 vCPU, 2 GiB), IaC via `deploy.sh` |
| Communication | OkHttp WebSocket, ADK bidi-streaming (LiveRequest/LiveEvent protocol) |

---

## Project Structure

```
fixitbuddy/
├── android/                    # Native Android app
│   ├── app/src/main/java/ai/fixitbuddy/app/
│   │   ├── core/               # Camera, Audio, WebSocket, Config, DI
│   │   ├── features/           # Session, History, Settings, Onboarding
│   │   ├── navigation/         # Compose Navigation
│   │   └── ui/                 # StatusIndicator, TranscriptOverlay, CameraViewfinder
│   └── app/src/test/           # 109 unit tests
├── backend/
│   ├── fixitbuddy/             # ADK agent directory
│   │   ├── agent.py            # Agent definition + system prompt
│   │   ├── tools.py            # 3 custom tools + embedded knowledge base
│   │   └── config.py           # Environment config
│   ├── tests/                  # 115 unit tests
│   ├── Dockerfile              # Container (python:3.12-slim → adk web)
│   └── deploy.sh               # IaC Cloud Run deployment
└── docs/
    ├── blog-post.md            # Published blog post
    └── devpost-submission.md   # Submission template
```

---

## Getting Started

### Prerequisites

- Android Studio (latest)
- Google Cloud project with Gemini API key
- Python 3.12+
- gcloud CLI

### Backend Setup

```bash
cd backend

# Install dependencies
pip install -r requirements.txt

# Local development (Gemini API key, not Vertex AI)
export GOOGLE_GENAI_USE_VERTEXAI=FALSE
export GOOGLE_API_KEY=your-api-key-here
adk web --port 8080 --host 0.0.0.0 .

# Deploy to Cloud Run
chmod +x deploy.sh
GOOGLE_CLOUD_PROJECT=your-project-id GOOGLE_API_KEY=your-key ./deploy.sh
```

### Android Setup

```bash
cd android

# Backend URL is configured in gradle.properties
# For local dev: BACKEND_URL=http://10.0.2.2:8080
# For production: BACKEND_URL=https://your-cloud-run-url.run.app

./gradlew assembleDebug
# Install APK on device or emulator
```

---

## Agent Tools

| Tool | Purpose |
|------|---------|
| `lookup_equipment_knowledge` | Query curated knowledge base for diagnostic procedures, error codes, and visual cues |
| `get_safety_warnings` | Get safety warnings before any physical action (electrical, mechanical, fluid, heat, etc.) |
| `log_diagnostic_step` | Record each diagnostic step for the session transcript |

---

## Knowledge Base

7 equipment documents across 3 categories, with 33 error codes and 28 diagnostic procedures:

- **Automotive**: Engine oil system (P0520-P0524), car battery/electrical (P0562-P0621), cooling system (P0115-P0128)
- **Electrical**: Residential breaker panel, GFCI outlets
- **Appliances**: Washing machine (E1-E4, F1-F21, UE, OE, LE, dE, IE), dishwasher (E1-E25), LG refrigerator (Er IF-Er SS, CL, dH)

Each document includes diagnostic steps with visual cues, common issues with root causes and fixes, error code mappings, and safety notes.

---

## Safety First

FixIt Buddy always calls `get_safety_warnings()` before guiding any physical action. The agent will stop and recommend calling a professional if the situation appears dangerous. Safety categories include electrical, mechanical, fluid, pressure, heat, and chemical hazards.

---

## Testing

```bash
# Backend (115 tests)
cd backend && python -m pytest tests/ -v

# Android (109 tests)
cd android && ./gradlew testDebugUnitTest
```

---

## License

MIT

---

## Built For

[Gemini Live Agent Challenge](https://geminiliveagentchallenge.devpost.com/) — Google's hackathon for multimodal AI agents that see, hear, speak, and create.

**Team**: Max Safari
