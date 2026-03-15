"""Telemetry callback tests for live session configuration."""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import patch

from google.genai import types

from fixitbuddy.telemetry import before_model_callback
from google.adk.models.llm_request import LlmRequest


def _fake_context():
    return SimpleNamespace(
        agent_name="fixitgenie",
        invocation_id="inv-123",
        session=SimpleNamespace(id="session-123"),
        user_id="user-123",
        state={},
    )


def test_before_model_callback_enables_transcription_and_fast_vad_for_audio():
    request = LlmRequest(
        model="gemini-2.5-flash-native-audio-latest",
        live_connect_config=types.LiveConnectConfig(response_modalities=["AUDIO"]),
    )

    with patch("fixitbuddy.telemetry.logger"):
        before_model_callback(_fake_context(), request)

    live = request.live_connect_config
    assert live.input_audio_transcription is not None
    assert live.output_audio_transcription is not None
    assert live.media_resolution == types.MediaResolution.MEDIA_RESOLUTION_HIGH
    assert live.realtime_input_config is not None
    assert (
        live.realtime_input_config.activity_handling
        == types.ActivityHandling.START_OF_ACTIVITY_INTERRUPTS
    )
    assert live.realtime_input_config.automatic_activity_detection is not None
    assert (
        live.realtime_input_config.automatic_activity_detection.silence_duration_ms
        == 220
    )
    assert (
        live.realtime_input_config.automatic_activity_detection.end_of_speech_sensitivity
        == types.EndSensitivity.END_SENSITIVITY_HIGH
    )


def test_before_model_callback_does_not_force_audio_config_for_text_only():
    request = LlmRequest(
        model="gemini-2.5-flash",
        live_connect_config=types.LiveConnectConfig(response_modalities=["TEXT"]),
    )

    with patch("fixitbuddy.telemetry.logger"):
        before_model_callback(_fake_context(), request)

    live = request.live_connect_config
    assert live.input_audio_transcription is None
    assert live.output_audio_transcription is None
    assert live.realtime_input_config is None
