# FixIt Buddy — Session Memory

## Project Overview
Multimodal AI agent Android app for the Gemini Live Agent Challenge.
See. Hear. Fix. — point your camera at equipment, describe the problem, get step-by-step guidance.

## Key Decisions
- **Android-native** (not web) — Kotlin + Jetpack Compose + Material 3
- **Backend**: Python ADK bidi-streaming agent on Google Cloud Run
- **Database**: Firestore (knowledge base + session logs)
- **AI Model**: gemini-2.5-flash (verified working with live API; google_search removed — cannot combine built-in + custom tools)
- **100% Google Cloud** — Firebase Hosting not needed (native app)
- **Package**: `ai.fixitbuddy.app`
- **Monorepo**: `fixitbuddy/android/` + `fixitbuddy/backend/`
- **ADK Structure**: `backend/fixitbuddy/` subdirectory (ADK expects agents_dir/agent_name/agent.py)
- **root_agent**: Must export `root_agent = agent` in agent.py for ADK discovery

## Patterns Source
- Android scaffolding/DI/MVVM: from `overpriced/overpriced-android-app/`
- Firebase/Firestore: from `Legacy/legacy-rational-investor/`
- ADK bidi-streaming: from Google ADK samples + docs

## Demo Scenarios
1. Car under the hood (oil, battery, coolant)
2. Home electrical breaker panel
3. Appliance error codes (washing machine)

## Hackathon Details
- **Deadline**: March 16, 2026, 5:00 PM PT
- **Category**: The Live Agent
- **Grand Prize**: $25K + trip to Google Cloud Next 2026
- **Judging**: Innovation & Multimodal UX (40%) + Technical Implementation (30%) + Demo & Presentation (30%)
- **Bonus**: Blog (+0.6), IaC (+0.2), GDG (+0.2)

## Build Status
- [x] Backend scaffolded (agent.py, tools.py, config.py, seed_knowledge.py)
- [x] Backend infrastructure (Dockerfile, requirements.txt, deploy.sh, .env.example)
- [x] Backend verified — all tools pass, agent definition loads correctly
- [x] Backend ADK restructured — fixitbuddy/ subdirectory with root_agent export
- [x] Backend tests — 88 unit tests passing (test_tools, test_agent, test_knowledge_integrity, test_websocket_e2e)
- [x] Backend integration tests — 27 tests against live ADK server
- [x] Android scaffolded (Gradle, manifest, Application, Activity, Theme, DI)
- [x] Android core (CameraManager, AudioStreamManager, AgentWebSocket)
- [x] Android UI (SessionScreen, SessionViewModel, StatusIndicator, TranscriptOverlay, CameraViewfinder)
- [x] Android secondary (HistoryScreen, SettingsScreen, Navigation)
- [x] Android app icon (adaptive icon with wrench, orange background)
- [x] Knowledge base embedded in tools.py (7 docs, 33 error codes, 28 procedures)
- [x] CLAUDE.md + README.md + PROGRESS.md + MEMORY.md
- [x] Architecture diagram (Mermaid)
- [x] Blog post draft (~1,800 words)
- [x] Devpost submission template
- [x] Code review — issues found and fixed (deprecated imports, annotation targets, ViewModel state bugs)
- [x] .gitignore (root + android)
- [x] **APK builds successfully** — assembleDebug BUILD SUCCESSFUL (22MB APK)
- [x] **Android unit tests pass** — 109/109 tests (4 test classes)
- [x] **ViewModel bugs fixed** — stopSession now clears errorMessage, DISCONNECTED after ERROR transitions to Idle
- [x] **Backend tested live with Gemini 2.5 Flash** — agent responds with proper tool calls (safety warnings, knowledge base, diagnostic logging)
- [x] **Agent model switched** — gemini-2.0-flash-live-001 → gemini-2.5-flash (old model unavailable; google_search removed)
- [x] **GitHub repo initialized** — https://github.com/dabra-labs/fixbuddy.git (needs auth push from local machine)
- [ ] Knowledge base seeded to Firestore
- [ ] Deployed to Cloud Run
- [ ] Demo video recorded (4 min)
- [ ] Submitted to Devpost
- [ ] Blog published to Medium/Dev.to
- [ ] GDG membership (+0.2 bonus)

## AAPT2 Workaround (aarch64 VM)
The development VM is aarch64 but Android SDK tools are x86_64. Solved by:
1. Downloaded `qemu-user-static` arm64 deb from Ubuntu ports
2. Extracted `qemu-x86_64-static` (aarch64-native binary that emulates x86_64)
3. Wrapped SDK build tools (aapt2, aapt, aidl, zipalign, etc.) with shell scripts that invoke qemu
4. Set `android.aapt2FromMavenOverride` in gradle.properties to use wrapped AAPT2
5. Downloaded JDK 17 (Temurin) for aarch64 since Gradle 9.3.1 requires JVM 17+

## Project Stats
- **~60 files** total
- **22 Kotlin files** — Android app (18 source + 4 test)
- **9 Python files** — Backend (4 source + 5 test)
- **10 XML files** — Android resources
- **3 Gradle Kotlin files** — Build config
- **6 Markdown files** — Documentation
- **~4,500 lines** of source code + tests
- **115 backend tests** passing
- **109 Android tests** passing
- **22MB debug APK** built successfully

## Test Summary
| Suite | Tests | Status |
|-------|-------|--------|
| Backend: test_tools.py | 35 | PASS |
| Backend: test_agent.py | 10 | PASS (updated for gemini-2.5-flash + 3 tools) |
| Backend: test_knowledge_integrity.py | 17 | PASS |
| Backend: test_websocket_e2e.py | 26 | PASS (updated for gemini-2.5-flash + 3 tools) |
| Backend: test_integration.py | 27 | PASS (requires server) |
| Android: AgentWebSocketTest | 29 | PASS |
| Android: AppConfigTest | 38 | PASS |
| Android: SessionViewModelTest | 47 | PASS (2 bugs found and fixed) |
| Android: RoutesTest | 18 | PASS (was 15, counted wrong earlier) |
| **TOTAL** | **197+27** | **ALL PASS** |
