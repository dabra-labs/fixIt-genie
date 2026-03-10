package ai.fixitbuddy.app.core.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Instrumented integration tests for [GlassesCameraManager].
 *
 * Tests verify initialization, SDK wiring, state management, and the full I420→JPEG
 * conversion pipeline using real Android APIs (YuvImage).
 *
 * Frame streaming tests are skipped due to a native crash in MockDeviceKit v0.4.0
 * (libdatax_jni.so / FakeLinkedDeviceImpl) when processing video frames — a known bug
 * in the developer preview SDK, not in our code. The NV21 byte layout is validated in
 * GlassesCameraManagerConversionTest (unit test, no device needed).
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class GlassesCameraManagerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var manager: GlassesCameraManager
    private lateinit var mockDeviceKit: MockDeviceKitInterface

    @Before
    fun setup() {
        manager = GlassesCameraManager(context)
        mockDeviceKit = MockDeviceKit.getInstance(context)
        grantPermission("android.permission.BLUETOOTH")
        grantPermission("android.permission.BLUETOOTH_CONNECT")
    }

    @After
    fun tearDown() {
        manager.stopStream()
        mockDeviceKit.reset()
    }

    @Test
    fun initialStateIsDisconnected() {
        assertEquals(GlassesState.DISCONNECTED, manager.connectionState.value)
    }

    @Test
    fun initializeDoesNotCrash() {
        manager.initialize()
    }

    @Test
    fun stopStreamOnIdleManagerDoesNotCrash() {
        manager.initialize()
        manager.stopStream()
        assertEquals(GlassesState.DISCONNECTED, manager.connectionState.value)
    }

    @Test
    fun stopStreamCalledTwiceDoesNotCrash() {
        manager.initialize()
        manager.stopStream()
        manager.stopStream()
        assertEquals(GlassesState.DISCONNECTED, manager.connectionState.value)
    }

    @Test
    fun mockDeviceKitPairsSuccessfully() = runBlocking {
        manager.initialize()
        val device = mockDeviceKit.pairRaybanMeta()
        device.powerOn()
        // Reaching here confirms MockDeviceKit pairing API works with our app
    }

    /**
     * Proves the full I420 → NV21 → JPEG pipeline using real Android YuvImage APIs.
     *
     * This is the critical test that was impossible in pure JVM unit tests.
     * Input: synthetic 8×8 solid-gray I420 frame (Y=128, U=128, V=128).
     * Expected output: a valid JPEG byte array starting with 0xFF 0xD8 and ending with 0xFF 0xD9.
     */
    @Test
    fun i420ToJpegPipelineProducesValidJpeg() {
        val width = 8; val height = 8
        val size = width * height        // 64 Y bytes
        val quarter = size / 4           // 16 U bytes, 16 V bytes

        // Build a solid-gray I420 frame: Y=128, U=128, V=128
        val i420 = ByteArray(size + quarter * 2) { 128.toByte() }

        // Step 1: I420 → NV21 (our conversion, internal access)
        val nv21 = manager.convertI420toNV21(i420, width, height)

        // Step 2: NV21 → JPEG via Android's YuvImage (real Android API — proves the pipeline)
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        val compressed = yuv.compressToJpeg(Rect(0, 0, width, height), 75, out)
        val jpeg = out.toByteArray()

        // compressToJpeg must succeed
        assertTrue("YuvImage.compressToJpeg() returned false", compressed)

        // JPEG must be non-trivially sized
        assertTrue("JPEG output is empty", jpeg.isNotEmpty())
        assertTrue("JPEG too small to be valid (${jpeg.size} bytes)", jpeg.size > 50)

        // JPEG Start-Of-Image marker: 0xFF 0xD8
        assertEquals("Missing JPEG SOI byte 0", 0xFF.toByte(), jpeg[0])
        assertEquals("Missing JPEG SOI byte 1", 0xD8.toByte(), jpeg[1])

        // JPEG End-Of-Image marker: 0xFF 0xD9
        assertEquals("Missing JPEG EOI byte -2", 0xFF.toByte(), jpeg[jpeg.size - 2])
        assertEquals("Missing JPEG EOI byte -1", 0xD9.toByte(), jpeg[jpeg.size - 1])
    }

    private fun grantPermission(permission: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
    }
}
