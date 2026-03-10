package ai.fixitbuddy.app.core.camera

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration tests for [GlassesCameraManager].
 *
 * Tests verify initialization, SDK wiring, and state management using MockDeviceKit.
 * Frame streaming tests are skipped here due to a native crash in MockDeviceKit v0.4.0
 * (libdatax_jni.so / FakeLinkedDeviceImpl) when processing video frames — a known bug
 * in the developer preview SDK, not in our code. The I420-JPEG conversion logic is
 * covered in GlassesCameraManagerConversionTest (unit test, no device needed).
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

    private fun grantPermission(permission: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
    }
}
