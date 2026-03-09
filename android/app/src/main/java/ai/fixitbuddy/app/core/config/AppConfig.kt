package ai.fixitbuddy.app.core.config

import ai.fixitbuddy.app.BuildConfig

/**
 * App-wide configuration constants.
 */
object AppConfig {
    /** Backend WebSocket URL. Set via gradle property BACKEND_URL. */
    val BACKEND_URL: String = BuildConfig.BACKEND_URL

    /** WebSocket endpoint path */
    const val WS_PATH = "/run_live"

    /** Full WebSocket URL */
    val WS_URL: String get() = BACKEND_URL.replace("https://", "wss://").replace("http://", "ws://") + WS_PATH

    /** Camera frame capture interval (ms) — Gemini processes at 1 FPS */
    const val FRAME_INTERVAL_MS = 1000L

    /** Camera frame size (pixels) — square, JPEG compressed */
    const val FRAME_SIZE = 768

    /** JPEG compression quality (0-100) */
    const val JPEG_QUALITY = 80

    /** Audio input sample rate (Hz) — Gemini expects 16kHz */
    const val AUDIO_INPUT_SAMPLE_RATE = 16000

    /** Audio output sample rate (Hz) — Gemini returns 24kHz */
    const val AUDIO_OUTPUT_SAMPLE_RATE = 24000

    /** App version display */
    const val VERSION = "1.0.0"
}
