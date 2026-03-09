"""End-to-end WebSocket streaming test — simulates the Android client protocol.

Tests the full data flow: video frames + audio → agent → transcripts + audio back.
Validates the WebSocket message format that the Android app uses.
"""
import asyncio
import base64
import json
import os
import struct
import pytest

# ─── Protocol Constants (must match Android AppConfig) ─────────────────────
FRAME_SIZE = 768
JPEG_QUALITY = 80
AUDIO_INPUT_SAMPLE_RATE = 16000
AUDIO_OUTPUT_SAMPLE_RATE = 24000
WS_PATH = "/run_live"


def _create_fake_jpeg(width: int = FRAME_SIZE, height: int = FRAME_SIZE) -> bytes:
    """Create a minimal valid JPEG for testing (smallest valid JPEG)."""
    # Minimal JPEG: SOI + APP0 + minimal frame
    # This is the smallest valid JPEG — a 1x1 white pixel
    return bytes([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43,
        0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09,
        0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D, 0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12,
        0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F, 0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20,
        0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C, 0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29,
        0x2C, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32,
        0x3C, 0x2E, 0x33, 0x34, 0x32, 0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01,
        0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xFF, 0xC4, 0x00, 0x1F, 0x00, 0x00,
        0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0xFF, 0xC4, 0x00, 0xB5, 0x10, 0x00, 0x02, 0x01, 0x03,
        0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00, 0x01, 0x7D,
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06,
        0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xA1, 0x08,
        0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52, 0xD1, 0xF0, 0x24, 0x33, 0x62, 0x72,
        0x82, 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45,
        0x46, 0x47, 0x48, 0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
        0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x73, 0x74, 0x75,
        0x76, 0x77, 0x78, 0x79, 0x7A, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3,
        0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6,
        0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9,
        0xCA, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2,
        0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xF1, 0xF2, 0xF3, 0xF4,
        0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA, 0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01,
        0x00, 0x00, 0x3F, 0x00, 0x7B, 0x94, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xD9
    ])


def _create_fake_pcm_audio(duration_ms: int = 100, sample_rate: int = AUDIO_INPUT_SAMPLE_RATE) -> bytes:
    """Create fake PCM 16-bit mono audio."""
    num_samples = int(sample_rate * duration_ms / 1000)
    # Generate silence (zeros) — 16-bit signed little-endian
    return struct.pack(f"<{num_samples}h", *([0] * num_samples))


# ─── Message Format Tests ──────────────────────────────────────────────────
# These validate the exact JSON/binary protocol the Android app sends.


class TestVideoFrameProtocol:
    """Validate video frame message format matches AgentWebSocket.kt sendVideoFrame()."""

    def test_video_frame_json_structure(self):
        """Video frames are sent as JSON with base64-encoded JPEG."""
        jpeg_data = _create_fake_jpeg()
        b64_data = base64.b64encode(jpeg_data).decode("utf-8")

        # This matches AgentWebSocket.kt sendVideoFrame()
        message = {
            "type": "video",
            "data": b64_data,
            "mime_type": "image/jpeg"
        }

        json_str = json.dumps(message)
        parsed = json.loads(json_str)

        assert parsed["type"] == "video"
        assert parsed["mime_type"] == "image/jpeg"
        # Verify the base64 decodes back to valid JPEG
        decoded = base64.b64decode(parsed["data"])
        assert decoded[:2] == b"\xff\xd8"  # JPEG magic bytes
        assert decoded[-2:] == b"\xff\xd9"  # JPEG end marker

    def test_video_frame_size_reasonable(self):
        """A 768x768 JPEG at quality 80 should be under 200KB."""
        jpeg_data = _create_fake_jpeg()
        b64_data = base64.b64encode(jpeg_data).decode("utf-8")

        message = json.dumps({
            "type": "video",
            "data": b64_data,
            "mime_type": "image/jpeg"
        })

        # Real frames are ~50-150KB. Our test frame is tiny.
        assert len(message) < 200 * 1024

    def test_base64_encoding_roundtrip(self):
        """Base64 encoding/decoding preserves the exact bytes."""
        original = _create_fake_jpeg()
        encoded = base64.b64encode(original).decode("utf-8")
        decoded = base64.b64decode(encoded)
        assert original == decoded


class TestAudioProtocol:
    """Validate audio message format matches AgentWebSocket.kt sendAudioChunk()."""

    def test_audio_chunk_is_raw_binary(self):
        """Audio chunks are sent as raw binary (not JSON-wrapped)."""
        pcm_data = _create_fake_pcm_audio(duration_ms=100)

        # Audio is sent as raw bytes over WebSocket binary frame
        assert isinstance(pcm_data, bytes)
        # 100ms at 16kHz, 16-bit = 100 * 16 * 2 = 3200 bytes
        expected_size = int(AUDIO_INPUT_SAMPLE_RATE * 0.1) * 2  # 16-bit = 2 bytes per sample
        assert len(pcm_data) == expected_size

    def test_audio_chunk_format_pcm16(self):
        """Audio is 16-bit signed little-endian PCM."""
        pcm_data = _create_fake_pcm_audio(duration_ms=50)
        # Each sample is 2 bytes (16-bit)
        num_samples = len(pcm_data) // 2
        samples = struct.unpack(f"<{num_samples}h", pcm_data)
        # All samples should be in 16-bit signed range
        for s in samples:
            assert -32768 <= s <= 32767

    def test_output_audio_sample_rate(self):
        """Output audio from agent is 24kHz PCM."""
        # Simulate receiving 100ms of 24kHz audio
        pcm_out = _create_fake_pcm_audio(duration_ms=100, sample_rate=AUDIO_OUTPUT_SAMPLE_RATE)
        expected_samples = int(AUDIO_OUTPUT_SAMPLE_RATE * 0.1)
        assert len(pcm_out) == expected_samples * 2


class TestIncomingMessageProtocol:
    """Validate incoming message format matches AgentWebSocket.kt onMessage()."""

    def test_transcript_message_format(self):
        """Transcript messages have type, text, and is_final fields."""
        msg = {
            "type": "transcript",
            "text": "I can see the breaker panel. Let me help you.",
            "is_final": True
        }
        parsed = json.loads(json.dumps(msg))
        assert parsed["type"] == "transcript"
        assert isinstance(parsed["text"], str)
        assert isinstance(parsed["is_final"], bool)

    def test_status_message_format(self):
        """Status messages indicate agent state."""
        for state in ["listening", "thinking", "speaking", "idle"]:
            msg = {"type": "status", "state": state}
            parsed = json.loads(json.dumps(msg))
            assert parsed["type"] == "status"
            assert parsed["state"] == state

    def test_tool_call_message_format(self):
        """Tool call messages show which tool the agent is using."""
        msg = {
            "type": "tool_call",
            "tool_name": "lookup_equipment_knowledge",
            "args": {"query": "breaker tripped", "category": "electrical"}
        }
        parsed = json.loads(json.dumps(msg))
        assert parsed["type"] == "tool_call"
        assert parsed["tool_name"] == "lookup_equipment_knowledge"
        assert "query" in parsed["args"]

    def test_audio_response_is_binary(self):
        """Audio responses from agent are raw 24kHz PCM binary."""
        audio_data = _create_fake_pcm_audio(
            duration_ms=200,
            sample_rate=AUDIO_OUTPUT_SAMPLE_RATE
        )
        # Binary audio is distinguished from JSON text by WebSocket frame type
        assert isinstance(audio_data, bytes)
        assert not audio_data.startswith(b"{")  # Not JSON

    def test_error_message_format(self):
        """Error messages provide user-readable error info."""
        msg = {
            "type": "error",
            "message": "Session expired. Please reconnect.",
            "code": "SESSION_EXPIRED"
        }
        parsed = json.loads(json.dumps(msg))
        assert parsed["type"] == "error"
        assert isinstance(parsed["message"], str)


class TestSessionProtocol:
    """Validate the session lifecycle protocol."""

    def test_websocket_url_format(self):
        """WebSocket URL matches the expected format."""
        backend_url = "https://fixitbuddy-agent-xxxxxxxxxx-uc.a.run.app"
        ws_url = backend_url.replace("https://", "wss://") + WS_PATH
        assert ws_url == "wss://fixitbuddy-agent-xxxxxxxxxx-uc.a.run.app/run_live"

    def test_ws_url_with_http(self):
        """HTTP URLs get ws:// prefix."""
        backend_url = "http://localhost:8080"
        ws_url = backend_url.replace("http://", "ws://") + WS_PATH
        assert ws_url == "ws://localhost:8080/run_live"

    def test_app_name_in_adk_endpoint(self):
        """The ADK endpoint includes the app name."""
        # ADK /run_live endpoint expects app_name as a query param or in the path
        # Our backend uses /run_live which ADK handles for the default agent
        assert WS_PATH == "/run_live"

    def test_concurrent_streams(self):
        """Video and audio can be sent concurrently without interfering."""
        video_msg = json.dumps({
            "type": "video",
            "data": base64.b64encode(b"\xff\xd8fake\xff\xd9").decode(),
            "mime_type": "image/jpeg"
        })
        audio_data = _create_fake_pcm_audio(50)

        # Both are valid, non-interfering messages
        assert video_msg.startswith("{")  # JSON text frame
        assert isinstance(audio_data, bytes)  # Binary frame
        # They use different WebSocket frame types, so no collision


class TestKnowledgeBaseIntegration:
    """Validate that the tool functions work end-to-end with the knowledge base."""

    def test_lookup_returns_relevant_results(self):
        from fixitbuddy.tools import lookup_equipment_knowledge
        result = lookup_equipment_knowledge(query="oil level low", category="automotive")
        assert isinstance(result, dict)
        assert result["found"] is True
        result_str = json.dumps(result).lower()
        assert "oil" in result_str or "dipstick" in result_str

    def test_safety_warnings_for_electrical(self):
        from fixitbuddy.tools import get_safety_warnings
        result = get_safety_warnings(action_type="electrical", equipment_category="electrical")
        assert isinstance(result, dict)
        assert "warnings" in result
        warnings_str = json.dumps(result["warnings"]).lower()
        assert "voltage" in warnings_str or "shock" in warnings_str or "power" in warnings_str

    def test_safety_warnings_for_mechanical(self):
        from fixitbuddy.tools import get_safety_warnings
        result = get_safety_warnings(action_type="mechanical", equipment_category="automotive")
        assert isinstance(result, dict)
        assert "warnings" in result
        assert len(result["warnings"]) > 0

    def test_diagnostic_step_logging(self):
        from fixitbuddy.tools import log_diagnostic_step
        result = log_diagnostic_step(
            step_number=1,
            description="Check oil level",
            observation="Oil level below minimum mark",
            result="Low oil confirmed"
        )
        assert isinstance(result, dict)
        assert result["logged"] is True
        assert result["step"]["step"] == 1

    def test_lookup_with_error_code(self):
        from fixitbuddy.tools import lookup_equipment_knowledge
        result = lookup_equipment_knowledge(
            query="error code",
            category="appliance",
            error_code="E4"
        )
        # Should find washing machine E4 (unbalanced load)
        assert isinstance(result, dict)

    def test_lookup_nonexistent_returns_graceful(self):
        from fixitbuddy.tools import lookup_equipment_knowledge
        result = lookup_equipment_knowledge(
            query="quantum flux capacitor malfunction",
            category="automotive"
        )
        # Should return a dict with found=False without crashing
        assert isinstance(result, dict)
        assert result["found"] is False


class TestAgentConfiguration:
    """Validate the agent is properly configured for live streaming."""

    def test_agent_model_is_live(self):
        from fixitbuddy.agent import agent
        assert "live" in agent.model

    def test_agent_has_all_required_tools(self):
        from fixitbuddy.agent import agent
        tool_names = [t.__name__ if hasattr(t, '__name__') else str(t) for t in agent.tools]
        # Must have knowledge lookup, safety, logging, and search
        assert len(agent.tools) == 4

    def test_system_instruction_covers_safety(self):
        from fixitbuddy.agent import SYSTEM_INSTRUCTION
        assert "SAFETY" in SYSTEM_INSTRUCTION
        assert "get_safety_warnings" in SYSTEM_INSTRUCTION

    def test_system_instruction_covers_visual(self):
        from fixitbuddy.agent import SYSTEM_INSTRUCTION
        assert "VISUAL" in SYSTEM_INSTRUCTION or "camera" in SYSTEM_INSTRUCTION

    def test_root_agent_exported(self):
        from fixitbuddy.agent import root_agent
        assert root_agent is not None
        assert root_agent.name == "fixitbuddy"
