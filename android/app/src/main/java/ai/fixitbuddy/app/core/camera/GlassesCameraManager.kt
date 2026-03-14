package ai.fixitbuddy.app.core.camera

import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages Ray-Ban Meta smart glasses camera streaming via the Meta Wearables DAT SDK.
 *
 * Emits JPEG frames via [frames] — identical interface to [CameraManager] — so
 * [ai.fixitbuddy.app.features.session.SessionViewModel] can swap sources transparently.
 *
 * Frame pipeline: Glasses (I420) → I420→NV21→JPEG conversion → [frames] SharedFlow
 *
 * Requires Android 12+ (API 31) — enforced by the Meta DAT SDK.
 */
@Singleton
class GlassesCameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 2)
    val frames: SharedFlow<ByteArray> = _frames

    private val _connectionState = MutableStateFlow(GlassesState.DISCONNECTED)
    val connectionState: StateFlow<GlassesState> = _connectionState

    private var streamSession: StreamSession? = null
    private var videoJob: Job? = null
    private var stateJob: Job? = null

    @Volatile private var initialized = false
    val isInitialized: Boolean get() = initialized
    private var consecutiveConversionFailures = 0

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Must be called once from [Activity.onCreate] after Android permissions are granted.
     * Safe to call multiple times — initializes only once.
     */
    @Synchronized
    fun initialize() {
        if (initialized) return
        try {
            Wearables.initialize(context)
            initialized = true
            Log.d(TAG, "Wearables SDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Wearables SDK failed to initialize — glasses unavailable", e)
            _connectionState.value = GlassesState.ERROR
            // initialized stays false; startStream() will surface ERROR state
        }
    }

    /**
     * Registers the app with Meta. Shows an in-place dialog (no app switching since SDK v0.4.0).
     * Call from an Activity context.
     */
    fun register(activity: Activity) {
        Wearables.startRegistration(activity)
    }

    // ── Camera Permission ──────────────────────────────────────────────────────

    /**
     * Checks whether the glasses camera permission has been granted.
     * Returns true if granted, false otherwise.
     */
    suspend fun hasCameraPermission(): Boolean {
        val result = Wearables.checkPermissionStatus(Permission.CAMERA)
        result.onFailure { e -> Log.e(TAG, "Failed to check glasses camera permission", e) }
        return result.getOrNull() == PermissionStatus.Granted
    }

    /**
     * Launches the Meta camera permission request via the provided [launcher].
     * Build the launcher in your Activity using [Wearables.RequestPermissionContract]:
     * ```kotlin
     * val launcher = registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
     *     val granted = result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted
     *     if (granted) glassesCameraManager.startStream()
     * }
     * glassesCameraManager.requestCameraPermission(launcher)
     * ```
     */
    fun requestCameraPermission(launcher: ActivityResultLauncher<Permission>) {
        launcher.launch(Permission.CAMERA)
    }

    // ── Streaming ──────────────────────────────────────────────────────────────

    /**
     * Starts the glasses camera stream. Frames are emitted via [frames].
     * Requires [initialize] to have been called first.
     * Requires camera permission — check [hasCameraPermission] first.
     */
    fun startStream() {
        if (!initialized) {
            Log.e(TAG, "startStream called before initialize() — SDK not ready")
            _connectionState.value = GlassesState.ERROR
            return
        }

        stopStream() // clean up any existing session

        _connectionState.value = GlassesState.CONNECTING
        Log.d(TAG, "Starting glasses stream")

        val session = Wearables.startStreamSession(
            context,
            AutoDeviceSelector(),
            StreamConfiguration(
                videoQuality = VideoQuality.MEDIUM,
                frameRate = 2  // minimum supported by SDK (valid values: 2, 7, 15, 24, 30)
            )
        ).also { streamSession = it }

        // Collect video frames
        videoJob = scope.launch {
            try {
                session.videoStream.collect { frame ->
                    val jpeg = convertI420toJpeg(frame)
                    if (jpeg != null) {
                        _frames.tryEmit(jpeg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video stream terminated unexpectedly", e)
                _connectionState.value = GlassesState.DISCONNECTED
            }
        }

        // Monitor stream state
        stateJob = scope.launch {
            try {
                session.state.collect { state ->
                    Log.d(TAG, "Stream state: $state")
                    _connectionState.value = when (state.name) {
                        "STREAMING" -> GlassesState.STREAMING
                        "STARTING"  -> GlassesState.CONNECTING
                        else        -> GlassesState.DISCONNECTED
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "State stream terminated unexpectedly", e)
                _connectionState.value = GlassesState.DISCONNECTED
            }
        }
    }

    /**
     * Stops the glasses camera stream and releases the session.
     */
    fun stopStream() {
        videoJob?.cancel()
        videoJob = null
        stateJob?.cancel()
        stateJob = null
        try {
            streamSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing glasses stream session", e)
        } finally {
            streamSession = null
        }
        _connectionState.value = GlassesState.DISCONNECTED
        Log.d(TAG, "Glasses stream stopped")
    }

    // ── Frame Conversion ───────────────────────────────────────────────────────

    /**
     * Converts a raw I420 VideoFrame from the glasses to a JPEG ByteArray.
     *
     * I420 format: [YYYY...][UU...][VV...] (planar YUV)
     * NV21 format: [YYYY...][VUVU...] (semi-planar, required by Android YuvImage)
     *
     * Conversion code ported verbatim from Meta's CameraAccess sample app:
     * StreamViewModel.convertI420toNV21() + handleVideoFrame()
     */
    private fun convertI420toJpeg(frame: VideoFrame): ByteArray? {
        return try {
            val buffer: ByteBuffer = frame.buffer
            val width = frame.width
            val height = frame.height
            val dataSize = buffer.remaining()

            // Copy buffer without consuming it
            val bytes = ByteArray(dataSize)
            val savedPos = buffer.position()
            buffer.get(bytes)
            buffer.position(savedPos)

            // I420 → NV21
            val nv21 = convertI420toNV21(bytes, width, height)

            // NV21 → JPEG
            val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            ByteArrayOutputStream().use { out ->
                yuv.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
                out.toByteArray()
            }.also { consecutiveConversionFailures = 0 }
        } catch (e: Exception) {
            consecutiveConversionFailures++
            if (consecutiveConversionFailures >= MAX_CONSECUTIVE_CONVERSION_FAILURES) {
                Log.e(TAG, "Frame conversion failed $consecutiveConversionFailures times in a row — " +
                    "possible SDK or hardware issue. Stopping stream.", e)
                _connectionState.value = GlassesState.DISCONNECTED
            } else {
                Log.w(TAG, "Frame conversion failed (${consecutiveConversionFailures}/" +
                    "$MAX_CONSECUTIVE_CONVERSION_FAILURES) — skipping frame", e)
            }
            null
        }
    }

    internal fun convertI420toNV21(input: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(input.size)
        val size = width * height
        val quarter = size / 4

        // Y plane is identical
        input.copyInto(output, 0, 0, size)

        // Interleave U and V planes: I420 has U then V; NV21 wants V then U
        for (n in 0 until quarter) {
            output[size + n * 2]     = input[size + quarter + n]  // V
            output[size + n * 2 + 1] = input[size + n]            // U
        }
        return output
    }

    companion object {
        private const val TAG = "GlassesCameraManager"
        private const val JPEG_QUALITY = 75
        private const val MAX_CONSECUTIVE_CONVERSION_FAILURES = 20
    }
}

/** Connection state of the Ray-Ban glasses. */
enum class GlassesState {
    DISCONNECTED,
    CONNECTING,
    STREAMING,
    ERROR          // SDK failed to initialize or an unrecoverable error occurred
}
