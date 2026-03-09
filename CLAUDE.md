# FixIt Buddy — CLAUDE.md

> Context file for AI assistants working on this project.

## Project Overview

**FixIt Buddy** is a multimodal AI agent that sees through your phone camera, hears you describe problems, and talks you through fixing equipment step-by-step. Built for the Google Gemini Live Agent Challenge.

## Architecture

```
┌─────────────────────────┐
│   Android App (Kotlin)  │
│  CameraX + AudioRecord  │
│  OkHttp WebSocket       │
│  Jetpack Compose UI     │
└──────────┬──────────────┘
           │ WebSocket (wss://)
           │ Video: JSON {type:"video", data:"<base64 JPEG>"}
           │ Audio: Binary PCM 16kHz
           ▼
┌─────────────────────────┐
│  Cloud Run (ADK Agent)  │
│  google-adk bidi-stream │
│  gemini-2.0-flash-live  │
│  Function calling tools │
└──────────┬──────────────┘
           │
     ┌─────┴─────┐
     ▼           ▼
┌─────────┐ ┌──────────┐
│Firestore│ │Google    │
│Knowledge│ │Search    │
│Base     │ │Grounding │
└─────────┘ └──────────┘
```

## Monorepo Structure

```
fixitbuddy/
├── android/          — Native Android app (Kotlin + Jetpack Compose)
│   ├── app/src/main/java/ai/fixitbuddy/app/
│   │   ├── core/         — Camera, Audio, WebSocket, DI, Config
│   │   ├── design/       — Theme (Color, Type, Theme)
│   │   ├── features/     — Session, History, Settings screens
│   │   └── navigation/   — NavHost
│   ├── gradle/           — Version catalog + wrapper
│   └── build.gradle.kts  — Root + app build scripts
├── backend/          — ADK agent on Cloud Run
│   ├── agent.py      — Agent definition + system prompt
│   ├── tools.py      — Function calling tools + embedded knowledge base
│   ├── config.py     — Environment config
│   ├── seed_knowledge.py — Firestore seeder
│   ├── Dockerfile    — Cloud Run container
│   ├── deploy.sh     — IaC deployment script
│   └── requirements.txt
├── docs/             — Architecture diagrams, screenshots
├── CLAUDE.md         — This file
├── MEMORY.md         — Build session memory
├── PROGRESS.md       — Phase-by-phase progress
└── README.md         — Project documentation
```

## Tech Stack

### Android
- **Language**: Kotlin 2.3
- **UI**: Jetpack Compose + Material 3
- **Camera**: CameraX 1.4.1 (preview + ImageAnalysis at 1 FPS)
- **Audio**: AudioRecord (16kHz PCM input) + AudioTrack (24kHz PCM output)
- **Network**: OkHttp WebSocket (bidi-streaming)
- **DI**: Hilt 2.59.2 (KSP)
- **Build**: AGP 9.0.1, Gradle 8.11.1, compileSdk 36, minSdk 26

### Backend
- **Framework**: Google ADK (Agent Development Kit)
- **Model**: gemini-2.0-flash-live-001 (bidi-streaming, multimodal)
- **Database**: Cloud Firestore (knowledge base + session logs)
- **Hosting**: Google Cloud Run
- **Tools**: lookup_equipment_knowledge, get_safety_warnings, log_diagnostic_step, google_search

## Key Patterns

- Package: `ai.fixitbuddy.app`
- MVVM architecture with StateFlow
- Singleton managers injected via Hilt
- Camera frames throttled to 1 FPS (768x768 JPEG)
- Audio: 16kHz mono PCM input, 24kHz mono PCM output
- WebSocket protocol: JSON for video/text, binary for audio

## Build Commands

```bash
# Android
cd android && ./gradlew assembleDebug

# Backend (local)
cd backend && pip install -r requirements.txt && adk api_server --port 8080 agent

# Deploy backend
cd backend && chmod +x deploy.sh && ./deploy.sh

# Seed Firestore
cd backend && python seed_knowledge.py
```

## Hackathon Details

- **Challenge**: Gemini Live Agent Challenge (Google)
- **Category**: The Live Agent
- **Judging**: Innovation & Multimodal UX (40%), Technical (30%), Demo (30%)
- **Bonus**: Blog post (+0.6), IaC (+0.2), GDG membership (+0.2)
