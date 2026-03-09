# FixIt Buddy

**Your AI-powered repair companion.** Point your phone camera at broken equipment, describe the problem, and get expert step-by-step voice guidance in real time.

Built with **Google ADK** + **Gemini Live API** for the [Gemini Live Agent Challenge](https://devpost.com/).

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
- **Appliances**: Decode washing machine error codes, fix dishwasher drain issues

Same architecture works for industrial equipment, HVAC, plumbing, and more.

---

## Architecture

```
┌─────────────────────┐     WebSocket      ┌──────────────────────┐
│   Android App       │◄──────────────────►│   Cloud Run          │
│                     │  Video (1 FPS)      │                      │
│  • CameraX          │  Audio (16kHz PCM)  │  • Google ADK Agent  │
│  • AudioRecord      │  Voice (24kHz PCM)  │  • Gemini 2.0 Flash  │
│  • Jetpack Compose   │  Transcripts        │  • Function Calling  │
│  • OkHttp WebSocket │                     │  • Bidi-Streaming    │
└─────────────────────┘                     └──────────┬───────────┘
                                                       │
                                              ┌────────┴────────┐
                                              │                 │
                                         ┌────┴────┐     ┌─────┴─────┐
                                         │Firestore│     │  Google   │
                                         │Knowledge│     │  Search   │
                                         │  Base   │     │ Grounding │
                                         └─────────┘     └───────────┘
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android App | Kotlin 2.3, Jetpack Compose, Material 3, CameraX, Hilt |
| Backend Agent | Google ADK, Gemini 2.0 Flash Live, Python 3.12 |
| Infrastructure | Google Cloud Run, Cloud Firestore, Vertex AI |
| Communication | OkHttp WebSocket, bidi-streaming (audio + video + text) |

---

## Project Structure

```
fixitbuddy/
├── android/              # Native Android app
│   ├── app/              # Main application module
│   │   └── src/main/java/ai/fixitbuddy/app/
│   │       ├── core/     # Camera, Audio, WebSocket, Config, DI
│   │       ├── design/   # Material 3 theme
│   │       ├── features/ # Session, History, Settings
│   │       └── navigation/
│   └── gradle/           # Version catalog
├── backend/              # ADK agent (Cloud Run)
│   ├── agent.py          # Agent definition + system prompt
│   ├── tools.py          # Function calling tools
│   ├── seed_knowledge.py # Firestore seeder
│   ├── Dockerfile        # Container image
│   └── deploy.sh         # IaC deployment
└── docs/                 # Diagrams and assets
```

---

## Getting Started

### Prerequisites

- Android Studio (latest)
- Google Cloud project with billing enabled
- Python 3.12+
- gcloud CLI

### Backend Setup

```bash
cd backend

# Local development (with API key)
export GOOGLE_GENAI_USE_VERTEXAI=FALSE
export GOOGLE_API_KEY=your-api-key-here
pip install -r requirements.txt
adk api_server --port 8080 agent

# Deploy to Cloud Run
chmod +x deploy.sh
./deploy.sh
```

### Android Setup

```bash
cd android

# Update backend URL in gradle.properties
# BACKEND_URL=https://your-cloud-run-url.run.app

./gradlew assembleDebug
# Install APK on device or emulator
```

### Seed Knowledge Base

```bash
cd backend
export GOOGLE_CLOUD_PROJECT=your-project-id
python seed_knowledge.py
```

---

## Agent Tools

| Tool | Purpose |
|------|---------|
| `lookup_equipment_knowledge` | Query curated knowledge base for diagnostic procedures |
| `get_safety_warnings` | Get safety warnings before any physical action |
| `log_diagnostic_step` | Record session steps for transcript |
| `google_search` | Look up specific model numbers and manufacturer info |

---

## Knowledge Base

7 equipment documents across 3 categories:

- **Automotive**: Engine oil system, car battery, cooling system
- **Electrical**: Residential breaker panel, GFCI outlets
- **Appliances**: Washing machine, dishwasher

Each document includes diagnostic steps with visual cues, common issues with root causes and fixes, error code mappings, and safety notes.

---

## Safety First

FixIt Buddy always calls `get_safety_warnings()` before guiding any physical action. It will stop and recommend a professional if the situation appears dangerous.

---

## License

MIT

---

## Built For

[Gemini Live Agent Challenge](https://devpost.com/) — Google's hackathon for multimodal AI agents.

**Team**: Max Safari
