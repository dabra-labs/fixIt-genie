#!/usr/bin/env python3
"""
FixIt Buddy — Live WebSocket test script.

Tests the full session flow:
  1. Create ADK session via REST
  2. Connect to /run_live WebSocket
  3. Send a text message (simulating user speech)
  4. Receive and print agent response
  5. Optionally send a JPEG image frame

Usage:
  python test_live.py                                   # uses deployed backend
  python test_live.py --url http://localhost:8080       # uses local backend
  python test_live.py --url http://localhost:8080 --image /path/to/photo.jpg
"""

import argparse
import asyncio
import base64
import json
import sys
import time
import urllib.request
import urllib.error

try:
    import websockets
except ImportError:
    print("Missing dependency: pip install websockets")
    sys.exit(1)

BACKEND_URL = "https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app"
APP_NAME = "fixitbuddy"
USER_ID = "test_script_user"


def create_session(base_url: str) -> str:
    """Create ADK session via REST, return session ID."""
    url = f"{base_url}/apps/{APP_NAME}/users/{USER_ID}/sessions"
    req = urllib.request.Request(
        url,
        data=b"{}",
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = json.loads(resp.read())
            session_id = body["id"]
            print(f"[+] Session created: {session_id}")
            return session_id
    except urllib.error.HTTPError as e:
        print(f"[-] Session creation failed: HTTP {e.code} — {e.read().decode()}")
        sys.exit(1)
    except Exception as e:
        print(f"[-] Session creation error: {e}")
        sys.exit(1)


def make_ws_url(base_url: str, session_id: str) -> str:
    ws_base = base_url.replace("https://", "wss://").replace("http://", "ws://")
    return (
        f"{ws_base}/run_live"
        f"?app_name={APP_NAME}"
        f"&user_id={USER_ID}"
        f"&session_id={session_id}"
        f"&modalities=AUDIO"   # native audio model requires AUDIO
        f"&proactive_audio=true"  # agent speaks first (greeting)
    )


def make_text_message(text: str) -> str:
    """ADK LiveRequest format for text input."""
    return json.dumps({
        "text": text
    })


def make_image_message(jpeg_bytes: bytes) -> str:
    """ADK LiveRequest format for an image frame (JPEG)."""
    b64 = base64.b64encode(jpeg_bytes).decode()
    return json.dumps({
        "blob": {
            "mime_type": "image/jpeg",
            "data": b64,
        }
    })


def make_audio_message(pcm_bytes: bytes) -> str:
    """ADK LiveRequest format for PCM audio at 16kHz."""
    b64 = base64.b64encode(pcm_bytes).decode()
    return json.dumps({
        "blob": {
            "mime_type": "audio/pcm;rate=16000",
            "data": b64,
        }
    })


def parse_event(raw: str) -> None:
    """Print relevant fields from an ADK Event JSON."""
    try:
        event = json.loads(raw)
    except json.JSONDecodeError:
        print(f"  [raw] {raw[:200]}")
        return

    author = event.get("author", "")
    partial = event.get("partial", False)
    content = event.get("content", {})
    parts = content.get("parts", []) if content else []

    for part in parts:
        if "text" in part:
            label = "[partial]" if partial else "[final]"
            print(f"  {label} {author}: {part['text']}")
        if "inlineData" in part:
            mime = part["inlineData"].get("mimeType", "")
            size = len(part["inlineData"].get("data", ""))
            print(f"  [audio] {mime} — {size} base64 chars")
        if "functionCall" in part:
            fn = part["functionCall"]
            print(f"  [tool_call] {fn.get('name')}({json.dumps(fn.get('args', {}))})")
        if "functionResponse" in part:
            fr = part["functionResponse"]
            resp_preview = str(fr.get("response", ""))[:100]
            print(f"  [tool_result] {fr.get('name')}: {resp_preview}")

    # Actions/interruptions
    actions = event.get("actions", {})
    if actions:
        if actions.get("turnComplete"):
            print("  [turn_complete]")
        if actions.get("interrupted"):
            print("  [interrupted]")


async def run_test(base_url: str, image_path: str | None) -> None:
    session_id = create_session(base_url)
    ws_url = make_ws_url(base_url, session_id)
    print(f"[+] Connecting to: {ws_url}\n")

    image_bytes: bytes | None = None
    if image_path:
        with open(image_path, "rb") as f:
            image_bytes = f.read()
        print(f"[+] Loaded image: {image_path} ({len(image_bytes)} bytes)\n")

    try:
        async with websockets.connect(ws_url, ping_interval=20, open_timeout=15) as ws:
            print("[+] WebSocket connected\n")

            # If we have an image, send it first so the agent can see context
            if image_bytes:
                await ws.send(make_image_message(image_bytes))
                print("[>] Sent image frame\n")
                await asyncio.sleep(0.5)

            # Send user text turn — native audio model accepts text input too
            user_text = json.dumps({
                "content": {
                    "role": "user",
                    "parts": [{"text": "Hi! My car engine won't start. Can you help?"}]
                }
            })
            await ws.send(user_text)
            print("[>] Sent user text turn\n")

            print("[*] Waiting for agent response (25s timeout)...\n")

            deadline = time.monotonic() + 25
            got_response = False

            while time.monotonic() < deadline:
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=1.0)
                    parse_event(raw)
                    got_response = True

                    # Stop after receiving a turn_complete event
                    try:
                        event = json.loads(raw)
                        if event.get("actions", {}).get("turnComplete"):
                            print("\n[+] Turn complete — test passed!")
                            break
                    except Exception:
                        pass

                except asyncio.TimeoutError:
                    if got_response:
                        # Got some response but no turn_complete yet — keep waiting
                        continue
                    # No response at all yet
                    sys.stdout.write(".")
                    sys.stdout.flush()

            if not got_response:
                print("\n[-] No response received within 25 seconds")

            await ws.close()

    except websockets.exceptions.ConnectionClosedError as e:
        print(f"\n[-] WebSocket closed unexpectedly: {e}")
        print("    This usually means the model failed to connect.")
        print("    Check Cloud Run logs: gcloud logging read ...")
        sys.exit(1)
    except OSError as e:
        print(f"\n[-] Connection failed: {e}")
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description="Test FixIt Buddy live session")
    parser.add_argument("--url", default=BACKEND_URL, help="Backend base URL")
    parser.add_argument("--image", default=None, help="Path to JPEG image to send as camera frame")
    args = parser.parse_args()

    print(f"FixIt Buddy Live Test")
    print(f"Backend: {args.url}")
    print(f"{'=' * 50}\n")

    asyncio.run(run_test(args.url, args.image))


if __name__ == "__main__":
    main()
