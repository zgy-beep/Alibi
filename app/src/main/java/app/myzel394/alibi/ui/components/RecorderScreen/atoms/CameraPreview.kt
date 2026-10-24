package app.myzel394.alibi.ui.components.RecorderScreen.atoms

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import app.myzel394.alibi.ui.utils.getCameraProvider
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraPreview(
    modifier: Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val bindingActive = remember { AtomicBoolean(true) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundPreviewUseCase by remember { mutableStateOf<Preview?>(null) }

    Box(modifier = modifier) {
        // Video preview
        AndroidView(
            factory = { context ->
                val previewView = PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                coroutineScope.launch {
                    val cameraProvider = context.getCameraProvider()
                    if (!bindingActive.get()) {
                        return@launch
                    }

                    try {
                        boundCameraProvider = cameraProvider
                        boundPreviewUseCase = preview
                        boundCameraProvider?.unbind(preview)
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (ex: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", ex)
                    }
                }
                previewView
            },
            onRelease = {
                bindingActive.set(false)
                val provider = boundCameraProvider
                val preview = boundPreviewUseCase
                if (provider != null && preview != null) {
                    runCatching { provider.unbind(preview) }
                }
                boundCameraProvider = null
                boundPreviewUseCase = null
            },
        )
    }
}
