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

    /**
     * Gemini Live audio can arrive in short bursts faster than realtime playback.
     * Keep a generous queue so playback stays smooth and we can still flush it on interruption.
     */
    const val AUDIO_PLAYBACK_QUEUE_CAPACITY = 1024

    /**
     * Batch a few short 24 kHz chunks before each AudioTrack write so playback
     * is less sensitive to scheduler/network jitter. 5760 B ~= 120 ms mono PCM16.
     */
    const val AUDIO_PLAYBACK_TARGET_WRITE_BYTES = 5760

    /**
     * After the first chunk in a batch arrives, wait briefly for the next chunk
     * so we can usually write a smoother 80-120 ms packet instead of tiny bursts.
     */
    const val AUDIO_PLAYBACK_BATCH_WAIT_MS = 80L

    /**
     * Apply a conservative gain boost to Gemini PCM output, but cap it per-chunk
     * so already-loud chunks do not clip.
     */
    const val AUDIO_OUTPUT_GAIN = 1.9f

    /**
     * While the agent is speaking, only forward mic chunks above this normalized
     * level so nearby user speech can still barge in, but low-level speaker leak
     * does not get fed straight back into Gemini.
     */
    const val AUDIO_BARGE_IN_LEVEL = 0.18f

    /** App version display */
    const val VERSION = "1.0.0"
}
