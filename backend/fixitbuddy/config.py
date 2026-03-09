"""FixIt Buddy — Configuration."""

from __future__ import annotations

import os

# Google Cloud
GOOGLE_CLOUD_PROJECT: str = os.environ.get("GOOGLE_CLOUD_PROJECT", "fixitbuddy")
GOOGLE_GENAI_USE_VERTEXAI: bool = os.environ.get("GOOGLE_GENAI_USE_VERTEXAI", "TRUE").upper() == "TRUE"
GOOGLE_API_KEY: str = os.environ.get("GOOGLE_API_KEY", "")

# Server
PORT: int = int(os.environ.get("PORT", "8080"))
HOST: str = os.environ.get("HOST", "0.0.0.0")

# Agent
AGENT_MODEL: str = os.environ.get("AGENT_MODEL", "gemini-2.5-flash-native-audio-latest")
AGENT_VOICE: str = os.environ.get("AGENT_VOICE", "Aoede")
