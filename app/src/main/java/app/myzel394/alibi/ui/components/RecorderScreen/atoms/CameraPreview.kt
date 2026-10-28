package app.myzel394.alibi.ui.components.RecorderScreen.atoms

import android.graphics.Outline
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.myzel394.alibi.R
import app.myzel394.alibi.services.SmoothZoom
import app.myzel394.alibi.services.ZoomRequestQueue
import app.myzel394.alibi.ui.utils.getCameraProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.math.abs

/**
 * Live camera viewfinder with pinch-to-zoom.
 *
 * The preview is meant to stay cheap: only a [Preview] use case is bound, no
 * capture use case, so the zoom is a pure viewfinder concern here (the video
 * recorder applies the chosen ratio to the recorded file itself).
 *
 * The camera binding lives in a plain (non-Compose-observable) holder so
 * recompositions caused by the zoom indicator never re-run the binding factory.
 */
@Composable
fun CameraPreview(
    modifier: Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    cameraKey: Any = cameraSelector,
    initialZoom: Float = 1f,
    onZoomChanged: ((ratio: Float, minRatio: Float, maxRatio: Float) -> Unit)? = null,
    onPreviewViewReady: ((PreviewView) -> Unit)? = null,
    onCameraReady: ((Camera?) -> Unit)? = null,
    cornerRadius: Dp = 0.dp,
) {
    // Switching the lens means a different camera and a different zoom range:
    // drop the whole preview state instead of reusing stale ratios. CameraSelector
    // has no equals(), and a caller may build a fresh selector on every
    // recomposition, so bindings are keyed by a stable identity (callers pass the
    // selected lens as [cameraKey]) instead of the selector instance.
    key(cameraKey) {
        CameraPreviewContent(
            modifier = modifier,
            cameraSelector = cameraSelector,
            initialZoom = initialZoom,
            onZoomChanged = onZoomChanged,
            onPreviewViewReady = onPreviewViewReady,
            onCameraReady = onCameraReady,
            cornerRadius = cornerRadius,
        )
    }
}

@Composable
private fun CameraPreviewContent(
    modifier: Modifier,
    cameraSelector: CameraSelector,
    initialZoom: Float,
    onZoomChanged: ((ratio: Float, minRatio: Float, maxRatio: Float) -> Unit)?,
    onPreviewViewReady: ((PreviewView) -> Unit)?,
    onCameraReady: ((Camera?) -> Unit)?,
    cornerRadius: Dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val binding = remember { CameraPreviewBinding(mainExecutor) }
    val smoothZoom = remember { SmoothZoom(initialZoom) }
    val zoomRequests = remember { ZoomRequestQueue() }

    val latestInitialZoom by rememberUpdatedState(initialZoom)
    val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val latestOnPreviewViewReady by rememberUpdatedState(onPreviewViewReady)
    val latestOnCameraReady by rememberUpdatedState(onCameraReady)

    var zoomIndicatorRatio by remember { mutableFloatStateOf(initialZoom) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    var zoomGestureGeneration by remember { mutableIntStateOf(0) }

    // Hide the indicator a short moment after the last pinch event.
    LaunchedEffect(zoomGestureGeneration) {
        if (zoomGestureGeneration == 0) {
            return@LaunchedEffect
        }
        delay(ZOOM_INDICATOR_HIDE_MILLIS)
        showZoomIndicator = false
    }

    // Eases the current ratio toward the gesture target on every frame and
    // submits at most one request at a time, so the camera never builds a
    // backlog of tweens while the fingers move.
    LaunchedEffect(binding, smoothZoom, zoomRequests) {
        var lastTick = SystemClock.elapsedRealtime()
        while (isActive) {
            delay(ZOOM_TICK_MILLIS)
            val now = SystemClock.elapsedRealtime()
            val deltaMillis = (now - lastTick).coerceIn(0L, MAX_ZOOM_TICK_MILLIS).toFloat()
            lastTick = now
            if (binding.camera == null) {
                continue
            }

            val ratio = smoothZoom.advance(deltaMillis)
            val lastSubmitted = binding.lastSubmittedRatio
            if (lastSubmitted.isNaN() || abs(ratio - lastSubmitted) > ZOOM_SUBMIT_EPSILON) {
                binding.lastSubmittedRatio = ratio
                submitZoomRequest(binding, zoomRequests, ratio)
            }
        }
    }

    val pinchModifier = Modifier.pointerInput(binding, smoothZoom) {
        detectTransformGestures { _, _, gestureZoom, _ ->
            val camera = binding.camera ?: return@detectTransformGestures
            if (!camera.isZoomSupported) {
                return@detectTransformGestures
            }
            if (!gestureZoom.isFinite() || gestureZoom <= 0f || gestureZoom == 1f) {
                return@detectTransformGestures
            }

            // Accumulate on the target ratio (not the lagging current ratio) so
            // fast pinches are not clamped to a single frame of movement.
            val target = smoothZoom.scaleBy(gestureZoom)
            zoomIndicatorRatio = target
            showZoomIndicator = true
            zoomGestureGeneration++
            latestOnZoomChanged?.invoke(
                target,
                smoothZoom.minRatio,
                smoothZoom.maxRatio,
            )
        }
    }

    Box(
        modifier = modifier.then(pinchModifier),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                val previewView = PreviewView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    setBackgroundColor(android.graphics.Color.BLACK)
                    applyRoundedClip(cornerRadius)
                }

                val preview = Preview.Builder()
                    .setResolutionSelector(VIEWFINDER_RESOLUTION_SELECTOR)
                    .build()
                binding.preview = preview
                preview.setSurfaceProvider(previewView.surfaceProvider)
                latestOnPreviewViewReady?.invoke(previewView)

                coroutineScope.launch {
                    try {
                        val cameraProvider = viewContext.getCameraProvider()
                        // The view may have been released while the provider was
                        // resolving; in that case never bind.
                        if (binding.released || binding.preview !== preview) {
                            return@launch
                        }
                        binding.cameraProvider = cameraProvider

                        preview.targetRotation =
                            previewView.display?.rotation ?: Surface.ROTATION_0

                        // Unbind only this preview in case a previous binding of
                        // the same use case is still around (lifecycle re-entry).
                        runCatching { cameraProvider.unbind(preview) }
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                        )
                        binding.camera = camera
                        latestOnCameraReady?.invoke(camera)

                        // The display can still be null while the sheet animates
                        // in; correct the rotation once the view is attached.
                        previewView.post {
                            previewView.display?.let { display ->
                                if (preview.targetRotation != display.rotation) {
                                    preview.targetRotation = display.rotation
                                }
                            }
                        }

                        val applied = applyInitialZoom(
                            binding = binding,
                            smoothZoom = smoothZoom,
                            zoomRequests = zoomRequests,
                            requestedRatio = latestInitialZoom,
                        )
                        zoomIndicatorRatio = applied
                        showZoomIndicator = false
                        latestOnZoomChanged?.invoke(
                            applied,
                            smoothZoom.minRatio,
                            smoothZoom.maxRatio,
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Use case binding failed", ex)
                    }
                }
                previewView
            },
            update = { previewView ->
                val preview = binding.preview
                val display = previewView.display
                if (preview != null && display != null &&
                    preview.targetRotation != display.rotation
                ) {
                    preview.targetRotation = display.rotation
                }
            },
            onRelease = {
                binding.released = true
                latestOnCameraReady?.invoke(null)
                val provider = binding.cameraProvider
                val preview = binding.preview
                if (provider != null && preview != null) {
                    runCatching { provider.unbind(preview) }
                }
                zoomRequests.reset()
                binding.cameraProvider = null
                binding.preview = null
                binding.camera = null
            },
        )

        if (showZoomIndicator) {
            Text(
                text = stringResource(
                    R.string.photo_overlay_zoom_value,
                    zoomIndicatorRatio,
                ),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Applies the requested ratio to the freshly bound camera once, so the preview
 * opens on the ratio the user picked before the sheet was dismissed.
 */
private fun applyInitialZoom(
    binding: CameraPreviewBinding,
    smoothZoom: SmoothZoom,
    zoomRequests: ZoomRequestQueue,
    requestedRatio: Float,
): Float {
    val zoomState = binding.camera?.cameraInfo?.zoomState?.value
    smoothZoom.setBounds(
        zoomState?.minZoomRatio ?: 1f,
        zoomState?.maxZoomRatio ?: 1f,
    )

    val applied = smoothZoom.snapTo(requestedRatio)
    binding.lastSubmittedRatio = applied
    submitZoomRequest(binding, zoomRequests, applied)
    return applied
}

private fun submitZoomRequest(
    binding: CameraPreviewBinding,
    zoomRequests: ZoomRequestQueue,
    ratio: Float,
) {
    val immediate = zoomRequests.submit(ratio)
    if (immediate != null) {
        applyZoomRequest(binding, zoomRequests, immediate)
    }
}

/**
 * Applies one zoom request and only then takes the next (newest) queued ratio.
 * Latest wins: intermediate ratios from a fast pinch are simply dropped.
 */
private fun applyZoomRequest(
    binding: CameraPreviewBinding,
    zoomRequests: ZoomRequestQueue,
    ratio: Float,
) {
    if (binding.released) {
        zoomRequests.reset()
        return
    }
    val cameraControl = binding.camera?.cameraControl
    if (cameraControl == null) {
        zoomRequests.reset()
        return
    }

    val future = runCatching { cameraControl.setZoomRatio(ratio) }.getOrNull()
    if (future == null) {
        zoomRequests.reset()
        return
    }

    future.addListener({
        if (binding.released) {
            zoomRequests.reset()
            return@addListener
        }
        val next = zoomRequests.complete()
        if (next != null) {
            applyZoomRequest(binding, zoomRequests, next)
        }
    }, binding.mainExecutor)
}

private val Camera.isZoomSupported: Boolean
    get() = runCatching {
        cameraInfo.zoomState.value?.maxZoomRatio?.let { it > 1f } == true
    }.getOrDefault(false)

/** Plain holder: mutating it must not trigger recomposition. */
private class CameraPreviewBinding(
    val mainExecutor: Executor,
) {
    var cameraProvider: ProcessCameraProvider? = null
    var preview: Preview? = null
    var camera: Camera? = null

    /** Last ratio handed to [ZoomRequestQueue], NaN until the first one. */
    var lastSubmittedRatio: Float = Float.NaN

    @Volatile
    var released = false
}

/**
 * Compose's `Modifier.clip` cannot reliably clip an interop view, so rounded
 * corners of compact previews are applied to the [PreviewView] itself.
 */
private fun PreviewView.applyRoundedClip(cornerRadius: Dp) {
    if (cornerRadius <= 0.dp) {
        return
    }
    val radiusPx = cornerRadius.value * resources.displayMetrics.density
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
        }
    }
    clipToOutline = true
}

private const val TAG = "CameraPreview"

/** ~60 Hz easing tick. */
private const val ZOOM_TICK_MILLIS = 16L

/** A stalled main thread must not make the easing jump in a single step. */
private const val MAX_ZOOM_TICK_MILLIS = 100L

private const val ZOOM_SUBMIT_EPSILON = 0.0005f

private const val ZOOM_INDICATOR_HIDE_MILLIS = 700L

/**
 * Pixel-dense 4:3 viewfinder instead of CameraX's 640x480 default, which is
 * visibly soft on high-density screens.
 */
private val VIEWFINDER_RESOLUTION_SELECTOR: ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            android.util.Size(1280, 960),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        )
    )
    .build()
