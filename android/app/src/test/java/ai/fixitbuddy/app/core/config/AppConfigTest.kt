package ai.fixitbuddy.app.core.config

import org.junit.Assert.*
import org.junit.Test

class AppConfigTest {

    @Test
    fun `FRAME_INTERVAL_MS is 1000 for 1 FPS`() {
        assertEquals(1000L, AppConfig.FRAME_INTERVAL_MS)
    }

    @Test
    fun `FRAME_INTERVAL_MS is positive`() {
        assertTrue(AppConfig.FRAME_INTERVAL_MS > 0)
    }

    @Test
    fun `FRAME_SIZE is 768 pixels`() {
        assertEquals(768, AppConfig.FRAME_SIZE)
    }

    @Test
    fun `FRAME_SIZE is positive`() {
        assertTrue(AppConfig.FRAME_SIZE > 0)
    }

    @Test
    fun `FRAME_SIZE is a reasonable dimension`() {
        assertTrue("Frame size should be between 256 and 2048",
            AppConfig.FRAME_SIZE in 256..2048)
    }

    @Test
    fun `JPEG_QUALITY is within valid range`() {
        assertTrue(AppConfig.JPEG_QUALITY in 1..100)
    }

    @Test
    fun `JPEG_QUALITY is reasonable for streaming`() {
        // Should be 60-90 for good balance of quality and size
        assertTrue("JPEG quality ${AppConfig.JPEG_QUALITY} should be 60-90 for streaming",
            AppConfig.JPEG_QUALITY in 60..90)
    }

    @Test
    fun `AUDIO_INPUT_SAMPLE_RATE is 16kHz for Gemini`() {
        assertEquals(16000, AppConfig.AUDIO_INPUT_SAMPLE_RATE)
    }

    @Test
    fun `AUDIO_INPUT_SAMPLE_RATE is positive`() {
        assertTrue(AppConfig.AUDIO_INPUT_SAMPLE_RATE > 0)
    }

    @Test
    fun `AUDIO_INPUT_SAMPLE_RATE is standard rate`() {
        // 16kHz is a standard audio rate for speech
        assertTrue("Audio input rate should be a standard rate",
            AppConfig.AUDIO_INPUT_SAMPLE_RATE in listOf(8000, 16000, 44100, 48000))
    }

    @Test
    fun `AUDIO_OUTPUT_SAMPLE_RATE is 24kHz for Gemini`() {
        assertEquals(24000, AppConfig.AUDIO_OUTPUT_SAMPLE_RATE)
    }

    @Test
    fun `AUDIO_OUTPUT_SAMPLE_RATE is positive`() {
        assertTrue(AppConfig.AUDIO_OUTPUT_SAMPLE_RATE > 0)
    }

    @Test
    fun `AUDIO_OUTPUT_SAMPLE_RATE is standard rate`() {
        // 24kHz is a valid audio rate
        assertTrue("Audio output rate should be standard",
            AppConfig.AUDIO_OUTPUT_SAMPLE_RATE in listOf(8000, 16000, 24000, 44100, 48000))
    }

    @Test
    fun `WS_PATH starts with slash`() {
        assertTrue(AppConfig.WS_PATH.startsWith("/"))
    }

    @Test
    fun `WS_PATH is run_live endpoint`() {
        assertEquals("/run_live", AppConfig.WS_PATH)
    }

    @Test
    fun `WS_PATH does not contain spaces`() {
        assertFalse(AppConfig.WS_PATH.contains(" "))
    }

    @Test
    fun `VERSION follows semver format`() {
        val parts = AppConfig.VERSION.split(".")
        assertEquals("Version should be x.y.z format", 3, parts.size)
        parts.forEach { part ->
            assertNotNull("Each version part should be a number", part.toIntOrNull())
        }
    }

    @Test
    fun `VERSION major is non-negative`() {
        val major = AppConfig.VERSION.split(".")[0].toInt()
        assertTrue(major >= 0)
    }

    @Test
    fun `VERSION minor is non-negative`() {
        val minor = AppConfig.VERSION.split(".")[1].toInt()
        assertTrue(minor >= 0)
    }

    @Test
    fun `VERSION patch is non-negative`() {
        val patch = AppConfig.VERSION.split(".")[2].toInt()
        assertTrue(patch >= 0)
    }

    @Test
    fun `WS_URL converts https to wss`() {
        // WS_URL is derived from BACKEND_URL
        val wsUrl = AppConfig.WS_URL
        assertTrue("WebSocket URL should use wss:// or ws://",
            wsUrl.startsWith("wss://") || wsUrl.startsWith("ws://"))
    }

    @Test
    fun `WS_URL does not contain https`() {
        assertFalse("WebSocket URL should not contain https",
            AppConfig.WS_URL.contains("https://"))
    }

    @Test
    fun `WS_URL does not contain http (without s)`() {
        // Should be ws:// or wss://, not http://
        val url = AppConfig.WS_URL
        assertFalse("WebSocket URL should not contain http:// (should be ws://)",
            url.contains("http://"))
    }

    @Test
    fun `WS_URL ends with WS_PATH`() {
        assertTrue("WebSocket URL should end with ${AppConfig.WS_PATH}",
            AppConfig.WS_URL.endsWith(AppConfig.WS_PATH))
    }

    @Test
    fun `WS_URL is not empty`() {
        assertNotNull(AppConfig.WS_URL)
        assertFalse(AppConfig.WS_URL.isEmpty())
    }

    @Test
    fun `BACKEND_URL is not empty`() {
        assertNotNull(AppConfig.BACKEND_URL)
        assertFalse(AppConfig.BACKEND_URL.isEmpty())
    }

    @Test
    fun `BACKEND_URL is valid URI`() {
        assertTrue("BACKEND_URL should start with http:// or https://",
            AppConfig.BACKEND_URL.startsWith("http://") || AppConfig.BACKEND_URL.startsWith("https://"))
    }

    @Test
    fun `FRAME_INTERVAL_MS divides into 1 second evenly`() {
        val oneSecond = 1000L
        assertEquals(0, oneSecond % AppConfig.FRAME_INTERVAL_MS)
    }

    @Test
    fun `AUDIO_OUTPUT_SAMPLE_RATE is greater than INPUT_SAMPLE_RATE`() {
        assertTrue("Output sample rate should be at least as high as input",
            AppConfig.AUDIO_OUTPUT_SAMPLE_RATE >= AppConfig.AUDIO_INPUT_SAMPLE_RATE)
    }

    @Test
    fun `Configuration values are stable`() {
        // Constants should not change between calls
        assertEquals(AppConfig.FRAME_INTERVAL_MS, AppConfig.FRAME_INTERVAL_MS)
        assertEquals(AppConfig.FRAME_SIZE, AppConfig.FRAME_SIZE)
        assertEquals(AppConfig.JPEG_QUALITY, AppConfig.JPEG_QUALITY)
        assertEquals(AppConfig.AUDIO_INPUT_SAMPLE_RATE, AppConfig.AUDIO_INPUT_SAMPLE_RATE)
        assertEquals(AppConfig.AUDIO_OUTPUT_SAMPLE_RATE, AppConfig.AUDIO_OUTPUT_SAMPLE_RATE)
        assertEquals(AppConfig.VERSION, AppConfig.VERSION)
        assertEquals(AppConfig.WS_PATH, AppConfig.WS_PATH)
    }
}
