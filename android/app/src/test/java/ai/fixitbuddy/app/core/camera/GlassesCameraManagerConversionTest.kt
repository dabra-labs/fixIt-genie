package ai.fixitbuddy.app.core.camera

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the I420→NV21 conversion logic in [GlassesCameraManager].
 * No device or SDK connection required.
 */
class GlassesCameraManagerConversionTest {

    private val manager = GlassesCameraManager(mockk(relaxed = true))

    @Test
    fun `I420 Y plane is preserved in NV21`() {
        val width = 4; val height = 4
        val size = width * height; val quarter = size / 4
        val i420 = ByteArray(size + quarter * 2) { it.toByte() }

        val nv21 = manager.convertI420toNV21(i420, width, height)

        for (i in 0 until size) assertEquals("Y[$i] mismatch", i420[i], nv21[i])
    }

    @Test
    fun `I420 UV planes are interleaved as VU in NV21`() {
        val width = 4; val height = 4
        val size = width * height; val quarter = size / 4
        val i420 = ByteArray(size + quarter * 2)
        for (i in 0 until size) i420[i] = i.toByte()
        for (i in 0 until quarter) i420[size + i] = (100 + i).toByte()           // U
        for (i in 0 until quarter) i420[size + quarter + i] = (200 + i).toByte() // V

        val nv21 = manager.convertI420toNV21(i420, width, height)

        for (n in 0 until quarter) {
            assertEquals("V[$n]", (200 + n).toByte(), nv21[size + n * 2])
            assertEquals("U[$n]", (100 + n).toByte(), nv21[size + n * 2 + 1])
        }
    }

    @Test
    fun `NV21 output size equals I420 input size`() {
        val width = 8; val height = 8
        val i420 = ByteArray(width * height + width * height / 2) { 128.toByte() }
        assertEquals(i420.size, manager.convertI420toNV21(i420, width, height).size)
    }

    @Test
    fun `handles minimum valid 2x2 frame`() {
        val width = 2; val height = 2; val size = width * height
        val i420 = ByteArray(size + size / 2) { it.toByte() }
        val nv21 = manager.convertI420toNV21(i420, width, height)
        assertNotNull(nv21)
        assertEquals(i420.size, nv21.size)
    }

    @Test
    fun `input array is not modified by conversion`() {
        val width = 4; val height = 4; val size = width * height
        val i420 = ByteArray(size + size / 2) { it.toByte() }
        val original = i420.copyOf()
        manager.convertI420toNV21(i420, width, height)
        assertTrue("Input must not be modified", i420.contentEquals(original))
    }
}
