package ai.fixitbuddy.app.features.session.components

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import ai.fixitbuddy.app.core.camera.CameraManager
import kotlinx.coroutines.launch

@Composable
fun CameraViewfinder(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        modifier = modifier,
        update = { previewView ->
            coroutineScope.launch {
                cameraManager.startCamera(lifecycleOwner, previewView)
            }
        }
    )
}
