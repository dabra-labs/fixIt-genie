#!/bin/bash
# FixIt Genie — Local Development Script
# Starts the ADK backend locally and optionally launches the Android emulator.
#
# Usage:
#   ./dev.sh              — start backend only
#   ./dev.sh --android    — start backend + launch Android emulator
#   ./dev.sh --android --avd Pixel_9_API_36   — specify AVD name
#
# Prerequisites:
#   Backend : Python 3.12+, pip install -r backend/requirements.txt
#   Android : Android Studio, AVD created (API 31+, minSdk requirement)
#             ANDROID_HOME or ANDROID_SDK_ROOT set, or Android Studio default path
set -euo pipefail

# ── Defaults ─────────────────────────────────────────────────────────────────
LAUNCH_ANDROID=0
AVD_NAME=""
BACKEND_PORT=8080

# ── Parse args ────────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --android) LAUNCH_ANDROID=1 ;;
    --avd) AVD_NAME="$2"; shift ;;
    --port) BACKEND_PORT="$2"; shift ;;
    --help|-h)
      sed -n '/^# Usage/,/^set/p' "$0" | head -n -1 | sed 's/^# \?//'
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
  shift
done

# ── Banner ────────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   FixIt Genie — Local Dev                    ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ── Resolve ANDROID_HOME ──────────────────────────────────────────────────────
resolve_android_home() {
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    echo "$ANDROID_HOME"
  elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
    echo "$ANDROID_SDK_ROOT"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    echo "$HOME/Library/Android/sdk"
  elif [ -d "$HOME/Android/Sdk" ]; then
    echo "$HOME/Android/Sdk"
  else
    echo ""
  fi
}

# ── Pre-flight: Backend ───────────────────────────────────────────────────────
echo "→ Checking backend prerequisites..."

if ! command -v python3 &>/dev/null; then
  echo "✗ python3 not found. Install Python 3.12+ from https://python.org"
  exit 1
fi

PYTHON_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
PYTHON_MAJOR=$(echo "$PYTHON_VERSION" | cut -d. -f1)
PYTHON_MINOR=$(echo "$PYTHON_VERSION" | cut -d. -f2)
if [ "$PYTHON_MAJOR" -lt 3 ] || { [ "$PYTHON_MAJOR" -eq 3 ] && [ "$PYTHON_MINOR" -lt 11 ]; }; then
  echo "✗ Python 3.11+ required (found $PYTHON_VERSION)"
  exit 1
fi

if ! command -v adk &>/dev/null; then
  echo "✗ ADK not found. Run: pip install -r backend/requirements.txt"
  exit 1
fi

if [ -z "${GOOGLE_API_KEY:-}" ]; then
  echo "⚠  GOOGLE_API_KEY is not set."
  echo "   Export it: export GOOGLE_API_KEY=your-key"
  echo "   Get a key: https://aistudio.google.com/apikey"
  exit 1
fi

echo "✓ Backend prerequisites OK (Python $PYTHON_VERSION, ADK $(adk --version 2>/dev/null || echo 'installed'))"
echo ""

# ── Pre-flight: Android (optional) ───────────────────────────────────────────
if [ "$LAUNCH_ANDROID" = "1" ]; then
  echo "→ Checking Android prerequisites..."

  ANDROID_HOME_RESOLVED=$(resolve_android_home)
  if [ -z "$ANDROID_HOME_RESOLVED" ]; then
    echo "✗ Android SDK not found."
    echo "  Install Android Studio and set ANDROID_HOME, or install SDK to ~/Library/Android/sdk"
    exit 1
  fi
  export ANDROID_HOME="$ANDROID_HOME_RESOLVED"

  EMULATOR="$ANDROID_HOME/emulator/emulator"
  ADB="$ANDROID_HOME/platform-tools/adb"

  if [ ! -x "$EMULATOR" ]; then
    echo "✗ Android emulator not found at $EMULATOR"
    echo "  Install via Android Studio → SDK Manager → SDK Tools → Android Emulator"
    exit 1
  fi

  # List available AVDs
  AVDS=$("$EMULATOR" -list-avds 2>/dev/null || echo "")
  if [ -z "$AVDS" ]; then
    echo "✗ No Android Virtual Devices found."
    echo "  Create one: Android Studio → Device Manager → Create Device"
    echo "  Minimum: API 31 (Android 12) — required by Meta DAT SDK"
    exit 1
  fi

  # Pick AVD
  if [ -z "$AVD_NAME" ]; then
    # Prefer API 36 → 35 → 34 → any
    AVD_NAME=$(echo "$AVDS" | grep -i "36\|35\|34" | head -1 || echo "$AVDS" | head -1)
  fi

  if ! echo "$AVDS" | grep -qx "$AVD_NAME"; then
    echo "✗ AVD '$AVD_NAME' not found. Available AVDs:"
    echo "$AVDS" | sed 's/^/    /'
    echo "  Use: ./dev.sh --android --avd <name>"
    exit 1
  fi

  echo "✓ Android SDK at $ANDROID_HOME"
  echo "✓ AVD: $AVD_NAME"
  echo ""
fi

# ── Start backend ─────────────────────────────────────────────────────────────
echo "→ Starting ADK backend on port $BACKEND_PORT..."
echo "  Backend URL for Android: http://10.0.2.2:$BACKEND_PORT"
echo "  (10.0.2.2 is the emulator's alias for the host machine)"
echo ""

cd backend
GOOGLE_GENAI_USE_VERTEXAI=FALSE \
  GOOGLE_API_KEY="$GOOGLE_API_KEY" \
  AGENT_MODEL="${AGENT_MODEL:-gemini-2.5-flash-native-audio-latest}" \
  adk web --port "$BACKEND_PORT" --host 0.0.0.0 . &
BACKEND_PID=$!
cd ..

echo "✓ Backend started (PID $BACKEND_PID)"
echo ""

# Wait for backend to be ready
echo "→ Waiting for backend to be ready..."
for i in $(seq 1 20); do
  if curl -s -o /dev/null --max-time 2 "http://localhost:$BACKEND_PORT/"; then
    echo "✓ Backend is ready at http://localhost:$BACKEND_PORT"
    break
  fi
  sleep 1
  if [ "$i" -eq 20 ]; then
    echo "⚠  Backend did not respond after 20s — check logs above"
  fi
done
echo ""

# ── Launch Android emulator (optional) ───────────────────────────────────────
if [ "$LAUNCH_ANDROID" = "1" ]; then
  echo "→ Launching Android emulator ($AVD_NAME)..."
  "$ANDROID_HOME/emulator/emulator" -avd "$AVD_NAME" -no-snapshot-load &
  EMULATOR_PID=$!
  echo "✓ Emulator launched (PID $EMULATOR_PID)"
  echo ""

  echo "→ Waiting for emulator to boot (this takes 30-60 seconds)..."
  "$ADB" wait-for-device
  # Wait for boot complete
  for i in $(seq 1 60); do
    BOOT_COMPLETE=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo "")
    if [ "$BOOT_COMPLETE" = "1" ]; then
      echo "✓ Emulator booted"
      break
    fi
    sleep 2
    if [ "$i" -eq 60 ]; then
      echo "⚠  Emulator boot timeout — check Android Studio"
    fi
  done
  echo ""

  echo "→ Building and installing debug APK..."
  cd android
  ./gradlew installDebug --quiet
  cd ..
  echo "✓ APK installed"
  echo ""

  echo "→ Launching FixIt Genie..."
  "$ADB" shell am start -n "ai.fixitbuddy.app/.MainActivity"
  echo "✓ App launched"
  echo ""
fi

# ── Running ───────────────────────────────────────────────────────────────────
echo "╔══════════════════════════════════════════════╗"
echo "║   FixIt Genie is running locally             ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "  Backend : http://localhost:$BACKEND_PORT"
echo "  ADK UI  : http://localhost:$BACKEND_PORT  (open in browser to test)"
if [ "$LAUNCH_ANDROID" = "1" ]; then
echo "  Android : running on emulator"
echo "  App URL : http://10.0.2.2:$BACKEND_PORT  (set in app Settings if needed)"
fi
echo ""
echo "  Press Ctrl+C to stop."
echo ""

# ── Cleanup on exit ───────────────────────────────────────────────────────────
cleanup() {
  echo ""
  echo "→ Shutting down..."
  kill "$BACKEND_PID" 2>/dev/null && echo "✓ Backend stopped" || true
  if [ "${EMULATOR_PID:-}" != "" ]; then
    kill "$EMULATOR_PID" 2>/dev/null && echo "✓ Emulator stopped" || true
  fi
}
trap cleanup EXIT INT TERM

wait "$BACKEND_PID"
