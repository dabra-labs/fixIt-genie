"""FixIt Buddy — Configuration."""
import os

# Google Cloud
GOOGLE_CLOUD_PROJECT = os.environ.get("GOOGLE_CLOUD_PROJECT", "fixitbuddy")
GOOGLE_GENAI_USE_VERTEXAI = os.environ.get("GOOGLE_GENAI_USE_VERTEXAI", "TRUE").upper() == "TRUE"
GOOGLE_API_KEY = os.environ.get("GOOGLE_API_KEY", "")

# Server
PORT = int(os.environ.get("PORT", "8080"))
HOST = os.environ.get("HOST", "0.0.0.0")

# Agent
AGENT_MODEL = os.environ.get("AGENT_MODEL", "gemini-2.5-flash-native-audio-preview-12-2025")
AGENT_VOICE = os.environ.get("AGENT_VOICE", "Aoede")
