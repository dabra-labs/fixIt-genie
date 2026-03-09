package ai.fixitbuddy.app.core.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import ai.fixitbuddy.app.core.config.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class CameraManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
    val frames: SharedFlow<ByteArray> = _frames

    private var lastFrameTime = 0L

    private val imageAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        val now = System.currentTimeMillis()
        if (now - lastFrameTime < AppConfig.FRAME_INTERVAL_MS) {
            imageProxy.close()
            return@Analyzer
        }
        lastFrameTime = now

        try {
            val bitmap = imageProxy.toBitmap()
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                AppConfig.FRAME_SIZE,
                AppConfig.FRAME_SIZE,
                true
            )
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, AppConfig.JPEG_QUALITY, stream)
            _frames.tryEmit(stream.toByteArray())

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        } catch (_: Exception) {
            // Frame processing error — skip this frame
        } finally {
            imageProxy.close()
        }
    }

    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val provider = suspendCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                Executors.newSingleThreadExecutor()
            )
        }
        cameraProvider = provider

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(Dispatchers.IO.asExecutor(), imageAnalyzer) }

        provider.unbindAll()
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        val currentState = cam.cameraInfo.torchState.value == TorchState.ON
        cam.cameraControl.enableTorch(!currentState)
        return !currentState
    }

    val isTorchOn: Boolean
        get() = camera?.cameraInfo?.torchState?.value == TorchState.ON

    val hasTorch: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true
}
