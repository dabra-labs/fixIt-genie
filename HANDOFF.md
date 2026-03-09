# FixIt Buddy — Handoff to Claude Code (VS Code)

## What This Project Is
Multimodal AI agent Android app for the **Gemini Live Agent Challenge** (Google hackathon).
- **Deadline**: March 16, 2026, 5:00 PM PT
- **Grand Prize**: $25K + trip to Google Cloud Next 2026
- **Concept**: Point phone camera at broken equipment, describe problem verbally, get step-by-step voice guidance from AI agent
- **Repo**: https://github.com/dabra-labs/fixbuddy.git (not yet pushed — needs `git push -u origin main`)

## Project Structure
```
fixitbuddy/
├── android/                    # Native Android app (Kotlin + Jetpack Compose)
│   ├── app/src/main/java/ai/fixitbuddy/app/
│   │   ├── FixItBuddyApp.kt          # Hilt Application class
│   │   ├── MainActivity.kt            # Single-activity, Compose host
│   │   ├── di/AppModule.kt            # Hilt dependency injection
│   │   ├── core/
│   │   │   ├── AppConfig.kt           # Constants (frame size, sample rates, URLs)
│   │   │   ├── CameraManager.kt       # CameraX preview + JPEG capture
│   │   │   └── AudioStreamManager.kt  # AudioRecord (16kHz) + AudioTrack (24kHz)
│   │   ├── network/
│   │   │   └── AgentWebSocket.kt      # OkHttp WebSocket to ADK /run_live
│   │   ├── features/session/
│   │   │   ├── SessionScreen.kt       # Main camera + voice UI (Compose)
│   │   │   └── SessionViewModel.kt    # Session state machine (Hilt ViewModel)
│   │   ├── features/history/
│   │   │   └── HistoryScreen.kt       # Past sessions list
│   │   ├── features/settings/
│   │   │   └── SettingsScreen.kt      # Backend URL, voice selection
│   │   ├── navigation/
│   │   │   ├── Routes.kt              # Navigation route definitions
│   │   │   └── AppNavigation.kt       # NavHost setup
│   │   └── ui/
│   │       ├── StatusIndicator.kt     # Animated listening/thinking/speaking dots
│   │       ├── TranscriptOverlay.kt   # Semi-transparent text overlay
│   │       └── CameraViewfinder.kt    # CameraX Preview composable
│   ├── app/src/test/                   # 109 unit tests (4 test classes)
│   ├── build.gradle.kts               # AGP 9.0.1, Kotlin 2.3, Compose, Hilt, CameraX
│   └── gradle.properties              # BACKEND_URL config
├── backend/
│   ├── fixitbuddy/                     # ADK agent directory (this name matters for ADK)
│   │   ├── __init__.py
│   │   ├── agent.py                   # Agent definition, root_agent export, SYSTEM_INSTRUCTION
│   │   ├── tools.py                   # 3 tools + embedded knowledge base (7 docs, 33 error codes)
│   │   └── config.py                  # Environment variable config
│   ├── tests/                          # 115 unit tests (5 test files)
│   │   ├── test_agent.py
│   │   ├── test_tools.py
│   │   ├── test_knowledge_integrity.py
│   │   ├── test_websocket_e2e.py
│   │   └── test_integration.py
│   ├── Dockerfile                      # Python 3.12-slim, ADK api_server
│   ├── deploy.sh                       # Cloud Run deployment script
│   ├── requirements.txt                # Production deps only
│   ├── requirements-dev.txt            # Adds pytest
│   ├── seed_knowledge.py               # Firestore seeder (optional, knowledge is embedded)
│   └── .env.example
├── CLAUDE.md                           # Project instructions for Claude
├── README.md
├── MEMORY.md                           # Session memory / decisions log
├── PROGRESS.md                         # Detailed progress tracker
└── .gitignore
```

## What's DONE and Actually Verified

### Backend (Python + Google ADK)
- **Agent definition**: `gemini-2.5-flash-native-audio-preview-12-2025` (configurable via `AGENT_MODEL` env var)
- **3 custom tools**: `lookup_equipment_knowledge`, `get_safety_warnings`, `log_diagnostic_step`
- **Knowledge base**: 7 equipment documents, 33 error codes, 28 diagnostic procedures — all embedded in tools.py
- **google_search removed**: Cannot combine built-in tools with custom Function Calling tools in gemini-2.5-flash
- **ADK server starts and discovers agent**: Verified — `adk api_server --port 8080 .` works
- **Agent tested live with Gemini API**: Sent a test prompt via REST `/run` endpoint with `AGENT_MODEL=gemini-2.5-flash`. Agent correctly called `get_safety_warnings` and `log_diagnostic_step`, gave proper step-by-step guidance.
- **115 backend tests passing**: `cd backend && python -m pytest tests/ -v`
- **Dockerfile verified**: Builds on python:3.12-slim, uses `adk api_server`

### Android (Kotlin + Jetpack Compose)
- **APK builds**: 22MB debug APK via `./gradlew assembleDebug`
- **109 unit tests passing**: `./gradlew testDebugUnitTest`
- **APK manifest verified**: Correct package (`ai.fixitbuddy.app`), permissions (CAMERA, RECORD_AUDIO, INTERNET, FLASHLIGHT), launcher activity (MainActivity), minSdk 26, targetSdk 36
- **2 ViewModel bugs found and fixed during testing**:
  1. `stopSession()` now clears `errorMessage`
  2. DISCONNECTED after ERROR now transitions to Idle
- **Tech stack**: Kotlin 2.3, AGP 9.0.1, Gradle 9.3.1, Jetpack Compose (BOM 2025.04.01), CameraX 1.4.1, Hilt 2.59.2, OkHttp 4.12.0

### Git
- 4 commits on `main` branch
- Remote set to `https://github.com/dabra-labs/fixbuddy.git`
- **NOT PUSHED** — VM couldn't authenticate with GitHub

## What's NOT Done (Be Honest)

### Critical — Must Do Before Submission
1. **Push to GitHub**: `git push -u origin main` from local machine
2. **Test app on emulator/device**: The app has NEVER been run on an actual Android device or emulator. Unit tests pass but the UI, camera, audio, and WebSocket connection are untested end-to-end. This is the biggest risk.
3. **Set BACKEND_URL**: Currently `https://fixitbuddy-agent-xxxxxxxxxx-uc.a.run.app` (placeholder). For local testing use `http://10.0.2.2:8080` (emulator→host). For production, deploy to Cloud Run first.
4. **Deploy backend to Cloud Run**: `cd backend && GOOGLE_CLOUD_PROJECT=rational-investor-cf3ff ./deploy.sh`
5. **End-to-end test**: App connects to backend via WebSocket, sends video frames + audio, receives transcripts + audio back
6. **Demo video**: 4 minutes, required for submission

### Important — Strong Bonus Points
7. **Blog post**: Draft exists but needs publishing to Medium/Dev.to (+0.6 bonus)
8. **Devpost submission**: Template exists, needs to be filled and submitted
9. **GDG membership**: Join Google Developer Group (+0.2 bonus)

### Known Issues / Likely Problems When Testing
- **WebSocket protocol**: The Android app sends video as JSON `{"type":"video","data":"<base64>","mime_type":"image/jpeg"}` and audio as raw binary PCM. The ADK `/run_live` endpoint may expect a different format — the ADK bidi-streaming protocol needs verification against actual ADK WebSocket docs.
- **Audio model requirement**: `gemini-2.5-flash-native-audio-preview-12-2025` requires audio input via bidiGenerateContent. It will NOT work via REST `/run` endpoint (gives "Cannot extract voices from non-audio request"). The REST endpoint works with `gemini-2.5-flash` for text testing.
- **Camera frame rate**: AppConfig sends frames at 2 FPS (500ms interval). May need tuning.
- **Audio playback**: 24kHz PCM output from agent needs to be played correctly via AudioTrack.
- **Error handling**: WebSocket reconnection logic exists in SessionViewModel but is untested with real disconnections.

## GCP Credentials
- **Project**: `rational-investor-cf3ff`
- **API Key**: `AIzaSyCMTQ4Lk6gGjoEpV0g40ugj_cprfRk8aSw`
- **For local dev**: Set `GOOGLE_GENAI_USE_VERTEXAI=false` and use the API key
- **For Cloud Run**: Set `GOOGLE_GENAI_USE_VERTEXAI=TRUE` (uses service account)

## Quick Start Commands

### Run backend locally
```bash
cd backend
pip install -r requirements.txt
GOOGLE_API_KEY=AIzaSyCMTQ4Lk6gGjoEpV0g40ugj_cprfRk8aSw \
GOOGLE_GENAI_USE_VERTEXAI=false \
AGENT_MODEL=gemini-2.5-flash \
adk api_server --port 8080 .
```

### Run backend tests
```bash
cd backend
pip install -r requirements-dev.txt
python -m pytest tests/ -v
```

### Build Android APK
```bash
cd android
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

### Run Android unit tests
```bash
cd android
./gradlew testDebugUnitTest
```

### Deploy to Cloud Run
```bash
cd backend
GOOGLE_CLOUD_PROJECT=rational-investor-cf3ff ./deploy.sh
# Then update BACKEND_URL in android/gradle.properties with the Cloud Run URL
```

### Push to GitHub
```bash
git push -u origin main
```

## Hackathon Judging Criteria
- **Innovation & Multimodal UX** (40%): Real-time camera + voice, visual awareness, spatial references
- **Technical Implementation** (30%): ADK agent with custom tools, knowledge base, safety system
- **Demo & Presentation** (30%): 4-min video showing 3 demo scenarios (car engine, breaker panel, washing machine)
- **Bonus**: Blog (+0.6), IaC/deploy.sh (+0.2), GDG membership (+0.2)
