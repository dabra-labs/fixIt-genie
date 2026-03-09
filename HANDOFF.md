# FixIt Buddy — Handoff

## What This Project Is
Multimodal AI agent Android app for the **Gemini Live Agent Challenge** (Google hackathon).
- **Deadline**: March 16, 2026, 5:00 PM PT
- **Grand Prize**: $25K + trip to Google Cloud Next 2026
- **Concept**: Point phone camera at broken equipment, describe problem verbally, get step-by-step voice guidance from AI agent
- **Repo**: https://github.com/dabra-labs/fixbuddy.git

## Status: FULLY WORKING END-TO-END ✅

Everything verified as of 2026-03-09:
1. Android app builds and runs on emulator
2. Backend deployed to Cloud Run — live at `https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app`
3. Session creation (REST) + WebSocket connection (ADK `/run_live`) verified
4. Gemini 2.5 Flash native audio model connects successfully
5. UI states: Ready → Listening → End Session → Ready all work correctly

## Architecture

```
Android App (Kotlin/Compose)
  └── OkHttp WebSocket (ADK LiveRequest protocol)
        └── Cloud Run: adk web server
              └── gemini-2.5-flash-native-audio-preview-12-2025 (Gemini API)
```

## Live Backend
- **URL**: `https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app`
- **GCP Project**: `rational-investor-cf3ff`
- **Region**: `us-central1`
- **Auth**: Gemini API key (NOT Vertex AI — native audio model is Gemini API only)

## Project Structure
```
fixitbuddy/
├── android/          — Native Android app (Kotlin + Jetpack Compose)
│   ├── app/src/main/java/ai/fixitbuddy/app/
│   │   ├── core/
│   │   │   ├── websocket/AgentWebSocket.kt   — ADK LiveRequest protocol
│   │   │   ├── audio/AudioStreamManager.kt   — 16kHz PCM in/out
│   │   │   └── camera/CameraManager.kt       — CameraX 1 FPS JPEG
│   │   └── features/
│   │       ├── session/SessionViewModel.kt   — Creates session, manages WS
│   │       └── settings/SettingsViewModel.kt — Persists backend URL to DataStore
│   └── gradle.properties  — BACKEND_URL set to live Cloud Run URL
├── backend/
│   ├── fixitbuddy/         — ADK agent directory (name matters!)
│   │   ├── agent.py        — root_agent definition
│   │   ├── tools.py        — lookup_equipment_knowledge, get_safety_warnings, log_diagnostic_step
│   │   └── config.py       — AGENT_MODEL env var config
│   ├── Dockerfile          — CMD: adk web (not adk api_server!)
│   ├── deploy.sh           — Cloud Run IaC script
│   └── .env                — GOOGLE_GENAI_USE_VERTEXAI=false, GOOGLE_API_KEY
```

## Critical Implementation Details (Lessons Learned)

### ADK Protocol
- **Must use `adk web`** not `adk api_server` — only `adk web` exposes `/run_live` WebSocket
- **Session creation required**: `POST /apps/fixitbuddy/users/{userId}/sessions` before WebSocket
- **WebSocket URL**: `wss://host/run_live?app_name=fixitbuddy&user_id={id}&session_id={id}&modalities=AUDIO`
- **LiveRequest format** (client→server): `{"blob":{"mime_type":"audio/pcm;rate=16000","data":"<base64>"}}`
- **LiveEvent format** (server→client): `{"content":{"parts":[{"text":"..."},{"inlineData":{"mimeType":"audio/pcm","data":"<base64>"}}]}}`

### Model
- `gemini-2.5-flash-native-audio-preview-12-2025` — ONLY works via Gemini API (not Vertex AI)
- Set `GOOGLE_GENAI_USE_VERTEXAI=FALSE` and `GOOGLE_API_KEY` in Cloud Run env vars
- Uses Voice Activity Detection (VAD) — do NOT send `activity_end` signals

## What Still Needs Doing (Submission Items)
1. **Demo video** (4 minutes, required) — show car engine + breaker panel + washing machine
2. **Blog post** — draft exists, needs publishing to Medium/Dev.to (+0.6 bonus)
3. **Devpost submission** — fill and submit the form
4. **GDG membership** — join a Google Developer Group (+0.2 bonus)

## Quick Start Commands

### Run backend locally
```bash
cd backend
source .venv/bin/activate  # or: pip install -r requirements.txt
adk web --port 8080 --host 0.0.0.0 .
```

### Build Android APK
```bash
cd android
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

### Run unit tests
```bash
cd android && ./gradlew test           # Android (all pass)
cd backend && python -m pytest tests/  # Backend (all pass)
```

### Deploy to Cloud Run
```bash
cd backend
GOOGLE_CLOUD_PROJECT=rational-investor-cf3ff \
GOOGLE_API_KEY=<key> \
./deploy.sh
```

## GCP Credentials
- **Project**: `rational-investor-cf3ff`
- **API Key**: In `backend/.env` as `GOOGLE_API_KEY`
- **Use Gemini API** (not Vertex AI): `GOOGLE_GENAI_USE_VERTEXAI=FALSE`

## Hackathon Judging
- **Innovation & Multimodal UX** (40%): Real-time camera + voice, visual awareness
- **Technical Implementation** (30%): ADK agent with custom tools, knowledge base, safety system
- **Demo & Presentation** (30%): 4-min video showing repair scenarios
- **Bonus**: Blog (+0.6), IaC/deploy.sh (+0.2), GDG membership (+0.2)
