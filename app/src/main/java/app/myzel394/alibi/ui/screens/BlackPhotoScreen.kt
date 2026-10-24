package app.myzel394.alibi.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.theme.LocalAlibiExtendedColors
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BlackPhotoScreen(
    photoCapture: PhotoCaptureModel,
    settings: AppSettings,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()
    val captureColors = LocalAlibiExtendedColors.current
    var showHint by rememberSaveable { mutableStateOf(true) }
    var showZoomIndicator by rememberSaveable { mutableStateOf(false) }
    var previewProgress by rememberSaveable {
        mutableFloatStateOf(
            if (settings.photoCaptureSettings.showPreviewOnLaunch) 1f else 0f
        )
    }
    var previewAttached by rememberSaveable {
        mutableStateOf(settings.photoCaptureSettings.showPreviewOnLaunch)
    }
    val captureLabel = stringResource(R.string.photo_overlay_take_photo)
    val exitLabel = stringResource(R.string.goBack)
    val screenDescription = stringResource(R.string.photo_black_hint)

    LaunchedEffect(previewProgress) {
        if (previewProgress > 0f) {
            previewAttached = true
        }
    }

    LaunchedEffect(Unit) {
        delay(2500)
        showHint = false
    }

    LaunchedEffect(photoCapture.state.zoomRatio) {
        if (photoCapture.state.zoomRatio != 1f) {
            showZoomIndicator = true
            delay(800)
            showZoomIndicator = false
        }
        if (settings.photoCaptureSettings.rememberZoom) {
            delay(450)
            photoCapture.rememberCurrentZoom()
        }
    }

    DisposableEffect(activity, lifecycleOwner) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.let {
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                activity?.isChangingConfigurations != true
            ) {
                photoCapture.stopSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun stopAndExit() {
        photoCapture.stopSession()
        onExit()
    }

    BackHandler {
        stopAndExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(captureColors.captureBackground)
            .pointerInput(photoCapture.isInSession, context.resources.displayMetrics.heightPixels) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startProgress = previewProgress
                    val revealDistance = context.resources.displayMetrics.heightPixels * 0.55f
                    val touchSlop = viewConfiguration.touchSlop
                    var totalVerticalDistance = 0f
                    var multiPointerGesture = false
                    var swipeClaimed = false

                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            multiPointerGesture = true
                        }

                        if (!multiPointerGesture) {
                            val change = event.changes
                                .firstOrNull { it.id == down.id }
                                ?: break
                            totalVerticalDistance +=
                                change.position.y - change.previousPosition.y
                            if (!swipeClaimed &&
                                abs(totalVerticalDistance) >= touchSlop
                            ) {
                                swipeClaimed = true
                            }
                            if (swipeClaimed) {
                                change.consume()
                                previewProgress = (
                                    startProgress -
                                        totalVerticalDistance / revealDistance
                                    ).coerceIn(0f, 1f)
                                showHint = false
                            }
                        } else if (swipeClaimed) {
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!multiPointerGesture && swipeClaimed) {
                        previewProgress = when {
                            previewProgress < 0.02f -> 0f
                            previewProgress > 0.98f -> 1f
                            else -> previewProgress
                        }
                    }
                }
            }
            .pointerInput(photoCapture.isInSession) {
                detectTransformGestures { _, _, zoom, _ ->
                        if (photoCapture.isInSession && zoom != 1f) {
                            photoCapture.zoomBy(zoom)
                        }
                    }
            }
            .pointerInput(photoCapture.state.canAcceptCapture) {
                detectTapGestures(
                    onTap = {
                        if (photoCapture.state.canAcceptCapture) {
                            photoCapture.capture()
                            showHint = false
                        }
                    },
                    onLongPress = {
                        stopAndExit()
                    },
                )
            }
            .semantics {
                contentDescription = screenDescription
                role = Role.Button
                onClick(label = captureLabel) {
                    if (photoCapture.state.canAcceptCapture) {
                        photoCapture.capture()
                        showHint = false
                        true
                    } else {
                        false
                    }
                }
                onLongClick(label = exitLabel) {
                    stopAndExit()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (previewAttached) {
            AndroidView(
                factory = { previewContext ->
                    PreviewView(previewContext).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        setBackgroundColor(android.graphics.Color.BLACK)
                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        photoCapture.setPreviewSurfaceProvider(surfaceProvider)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = previewProgress
                    },
            )
        }
        if (settings.photoCaptureSettings.gridLinesEnabled && previewProgress > 0.02f) {
            val gridAlpha = previewProgress * 0.42f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = androidx.compose.ui.graphics.Color.White.copy(alpha = gridAlpha)
                val stroke = 1.dp.toPx()
                for (index in 1..2) {
                    val fraction = index / 3f
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(size.width * fraction, 0f),
                        end = androidx.compose.ui.geometry.Offset(
                            size.width * fraction,
                            size.height,
                        ),
                        strokeWidth = stroke,
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height * fraction),
                        end = androidx.compose.ui.geometry.Offset(
                            size.width,
                            size.height * fraction,
                        ),
                        strokeWidth = stroke,
                    )
                }
            }
        }
        if (previewAttached) {
            DisposableEffect(photoCapture) {
                onDispose {
                    photoCapture.setPreviewSurfaceProvider(null)
                }
            }
        }

        if (showZoomIndicator) {
            Text(
                text = stringResource(
                    R.string.photo_zoom_indicator,
                    photoCapture.state.zoomRatio,
                ),
                color = captureColors.captureContent.copy(alpha = 0.78f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
            )
        }

        when {
            photoCapture.state.status == PhotoCaptureStatus.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        stringResource(R.string.photo_black_error),
                        color = captureColors.captureContent,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        photoCapture.state.error
                            ?: stringResource(R.string.photo_error_generic),
                        color = captureColors.captureContent.copy(alpha = 0.84f),
                    )
                    if (photoCapture.state.canRetrySave) {
                        TextButton(
                            onClick = photoCapture::retryLastSave,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = captureColors.captureContent,
                            ),
                        ) {
                            Text(stringResource(R.string.photo_action_retry_save))
                        }
                    }
                }
            }

            photoCapture.state.status == PhotoCaptureStatus.STARTING -> {
                Text(
                    stringResource(R.string.photo_black_starting),
                    color = captureColors.captureContent.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            showHint -> {
                Text(
                    stringResource(R.string.photo_black_hint),
                    color = captureColors.captureContent.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }

}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return current as? Activity
}
