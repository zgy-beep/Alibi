package app.myzel394.alibi.ui.screens

import android.Manifest
import android.content.Context
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.PhotoFlashMode
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.CameraPreview
import app.myzel394.alibi.ui.components.RecorderScreen.organisms.RecorderModel
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.utils.CameraInfo
import app.myzel394.alibi.ui.utils.KeepScreenOn
import app.myzel394.alibi.ui.utils.PermissionHelper
import app.myzel394.alibi.ui.utils.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private enum class StandardCameraMode {
    PHOTO,
    VIDEO,
}

private val ACTIVE_RECORDING_STATES = listOf(
    RecorderState.STARTING,
    RecorderState.RECORDING,
    RecorderState.PAUSED,
)

/**
 * System-camera-like screen (0.6.8).
 *
 * Unlike the covert photo/video modes, this screen keeps the viewfinder on
 * screen the whole time and supports tap-to-focus, pinch-to-zoom and a record
 * button. It is an opt-in entry from the home screen; the covert modes stay the
 * app default.
 */
@Composable
fun StandardCameraScreen(
    photoCapture: PhotoCaptureModel,
    videoRecorder: VideoRecorderModel,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onExit: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenPhotoSettings: () -> Unit,
    onOpenVideoSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(StandardCameraMode.PHOTO) }
    var cameraPermissionGranted by remember {
        mutableStateOf(PermissionHelper.hasGranted(context, Manifest.permission.CAMERA))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(cameraPermissionGranted) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isRecording = videoRecorder.recorderState in ACTIVE_RECORDING_STATES ||
        videoRecorder.isInRecording

    // Devices (and emulators) with a single camera must not offer a switch that
    // would fail to rebind.
    val canSwitchLens = remember(context) {
        CameraInfo.queryAvailableCameras(context).map { it.lens }.distinct().size > 1
    }

    KeepScreenOn()

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            if (!cameraPermissionGranted) {
                CameraPermissionPrompt(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onClose = onExit,
                )
            } else if (mode == StandardCameraMode.PHOTO) {
                PhotoCameraSurface(
                    photoCapture = photoCapture,
                    settings = settings,
                    canSwitchLens = canSwitchLens,
                    onOpenGallery = onOpenGallery,
                    onOpenPhotoSettings = onOpenPhotoSettings,
                    onSwitchToVideo = {
                        if (!isRecording) {
                            // The photo service must release the camera before the
                            // video preview binds it, otherwise the two camera
                            // owners race on the same device.
                            scope.launch {
                                runCatching { photoCapture.stopSession() }
                                val deadline = System.currentTimeMillis() + 3_000
                                while (photoCapture.isInSession &&
                                    System.currentTimeMillis() < deadline
                                ) {
                                    delay(40)
                                }
                                mode = StandardCameraMode.VIDEO
                            }
                        }
                    },
                    onClose = {
                        runCatching { photoCapture.stopSession() }
                        onExit()
                    },
                )
            } else {
                VideoCameraSurface(
                    videoRecorder = videoRecorder,
                    settings = settings,
                    canSwitchLens = canSwitchLens,
                    onOpenVideoSettings = onOpenVideoSettings,
                    onSwitchToPhoto = {
                        if (!isRecording) {
                            mode = StandardCameraMode.PHOTO
                        }
                    },
                    onClose = onExit,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Photo mode                                                          */
/* ------------------------------------------------------------------ */

@Composable
private fun PhotoCameraSurface(
    photoCapture: PhotoCaptureModel,
    settings: AppSettings,
    canSwitchLens: Boolean,
    onOpenGallery: () -> Unit,
    onOpenPhotoSettings: () -> Unit,
    onSwitchToVideo: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var shutterFlash by remember { mutableStateOf(false) }

    val flashAlpha by animateFloatAsState(
        targetValue = if (shutterFlash) 0.55f else 0f,
        animationSpec = tween(durationMillis = if (shutterFlash) 30 else 240),
        label = "shutterFlash",
    )

    LaunchedEffect(Unit) {
        if (!photoCapture.isInSession) {
            // Give a previous preview binding (video mode) a moment to release
            // the camera before the photo service binds it.
            delay(150)
            photoCapture.startSession(context, settings, PhotoCaptureMode.STANDARD)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { photoCapture.stopSession() }
        }
    }

    // The service may (re)create its Preview after a lens/flash reconfigure or
    // after the service connection completes, so re-attach on every relevant
    // state change. Both model calls are idempotent.
    LaunchedEffect(previewView, photoCapture.state.status, photoCapture.state.lensFacing) {
        val view = previewView ?: return@LaunchedEffect
        if (photoCapture.isInSession) {
            photoCapture.setPreviewSurfaceProvider(view.surfaceProvider)
            photoCapture.setMeteringPointFactory(view.meteringPointFactory)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            photoCapture.setPreviewSurfaceProvider(null)
            photoCapture.setMeteringPointFactory(null)
        }
    }

    // Like the covert black-screen mode, leaving the app stops the photo session
    // so the camera is not held in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                runCatching { photoCapture.stopSession() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(photoCapture.focusResult) {
        if (photoCapture.focusResult != null) {
            delay(900)
            focusPoint = null
            photoCapture.clearFocusResult()
        }
    }

    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(60)
            shutterFlash = false
        }
    }

    val status = photoCapture.state.status
    val canCapture =
        photoCapture.state.canAcceptCapture && status == PhotoCaptureStatus.READY
    val flashMode = photoCapture.state.flashMode
    val lensFacing = photoCapture.state.lensFacing

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isClickable = false
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (settings.photoCaptureSettings.gridLinesEnabled) {
            CameraGrid(modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(photoCapture.isInSession) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (photoCapture.isInSession && zoom != 1f && zoom > 0f) {
                            photoCapture.zoomBy(zoom)
                        }
                    }
                }
                .pointerInput(photoCapture.isInSession, status) {
                    detectTapGestures { offset ->
                        // Focusing while the camera is re-binding would report a
                        // spurious failure ring, so only focus a ready camera.
                        if (photoCapture.isInSession && status == PhotoCaptureStatus.READY) {
                            focusPoint = offset
                            photoCapture.requestFocus(offset.x, offset.y)
                        }
                    }
                },
        )

        focusPoint?.let { point ->
            FocusRing(
                center = point,
                focused = photoCapture.focusResult,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (abs(photoCapture.zoomRatio - 1f) > 0.05f) {
            ZoomChip(
                ratio = photoCapture.zoomRatio,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            )
        }

        CameraScrims()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha)),
        )

        TopControls(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            onClose = onClose,
            onOpenSettings = onOpenPhotoSettings,
            onSwitchLens = if (canSwitchLens) {
                {
                    photoCapture.reconfigure(
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        },
                    )
                }
            } else {
                null
            },
            trailingExtra = {
                FlashModeButton(
                    mode = flashMode,
                    onCycle = {
                        photoCapture.reconfigure(
                            flashMode = when (flashMode) {
                                PhotoFlashMode.OFF -> PhotoFlashMode.AUTO
                                PhotoFlashMode.AUTO -> PhotoFlashMode.ON
                                PhotoFlashMode.ON -> PhotoFlashMode.OFF
                            },
                        )
                    },
                )
            },
        )

        BottomControls(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            mode = StandardCameraMode.PHOTO,
            onSelectPhoto = {},
            onSelectVideo = onSwitchToVideo,
            leading = {
                IconButton(onClick = onOpenGallery) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.camera_standard_gallery),
                        tint = Color.White,
                    )
                }
            },
            primaryAction = {
                PrimaryShutter(
                    enabled = canCapture,
                    busy = status == PhotoCaptureStatus.CAPTURING ||
                        status == PhotoCaptureStatus.SAVING ||
                        status == PhotoCaptureStatus.STARTING,
                    onClick = {
                        shutterFlash = true
                        photoCapture.capture()
                    },
                )
            },
        )
    }
}

/* ------------------------------------------------------------------ */
/* Video mode                                                          */
/* ------------------------------------------------------------------ */

@Composable
private fun VideoCameraSurface(
    videoRecorder: VideoRecorderModel,
    settings: AppSettings,
    canSwitchLens: Boolean,
    onOpenVideoSettings: () -> Unit,
    onSwitchToPhoto: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isRecording = videoRecorder.recorderState in ACTIVE_RECORDING_STATES ||
        videoRecorder.isInRecording

    // Devices (and emulators) with a single camera must not offer a switch that
    // would fail to rebind.
    val canSwitchLens = remember(context) {
        CameraInfo.queryAvailableCameras(context).map { it.lens }.distinct().size > 1
    }
    var pendingRecording by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var preRecordCamera by remember { mutableStateOf<Camera?>(null) }
    var preRecordPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var servicePreviewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(videoRecorder.focusResult) {
        if (videoRecorder.focusResult != null) {
            delay(900)
            focusPoint = null
            videoRecorder.clearFocusResult()
        }
    }

    // Audio capture follows the RECORD_AUDIO permission, which the prepare sheet
    // would normally initialise; the standard screen starts the service directly.
    LaunchedEffect(Unit) {
        videoRecorder.init(context)
    }

    // The lightweight preview binding is torn down first (one frame), then the
    // recorder service takes over the camera and records.
    LaunchedEffect(pendingRecording) {
        if (!pendingRecording) {
            return@LaunchedEffect
        }
        delay(80)
        runCatching { videoRecorder.startRecording(context, settings) }
        delay(400)
        pendingRecording = false
    }

    LaunchedEffect(servicePreviewView, videoRecorder.recorderState) {
        val view = servicePreviewView ?: return@LaunchedEffect
        if (isRecording) {
            videoRecorder.setPreviewSurfaceProvider(view.surfaceProvider)
            videoRecorder.setMeteringPointFactory(view.meteringPointFactory)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoRecorder.setPreviewSurfaceProvider(null)
            videoRecorder.setMeteringPointFactory(null)
        }
    }

    fun stopAndSave(exitAfterwards: Boolean) {
        if (saving || !isRecording) {
            if (exitAfterwards) {
                onClose()
            }
            return
        }
        scope.launch {
            saving = true
            runCatching { videoRecorder.stopRecording(context) }
            runCatching {
                context.dataStore.updateData {
                    it.saveLastRecording(videoRecorder as RecorderModel)
                }
            }
            runCatching { videoRecorder.onRecordingSave(false).join() }
            runCatching { videoRecorder.destroyService(context) }
            saving = false
            if (exitAfterwards) {
                onClose()
            }
        }
    }

    BackHandler {
        if (saving) {
            return@BackHandler
        }
        if (isRecording) {
            stopAndSave(exitAfterwards = true)
        } else {
            onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isRecording) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        setBackgroundColor(android.graphics.Color.BLACK)
                        servicePreviewView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                cameraSelector = videoRecorder.cameraSelector,
                cameraKey = videoRecorder.cameraID,
                initialZoom = videoRecorder.zoomRatio,
                onZoomChanged = videoRecorder::onPreviewZoomChanged,
                onPreviewViewReady = { preRecordPreviewView = it },
                onCameraReady = { preRecordCamera = it },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isRecording) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        if (isRecording) {
                            videoRecorder.focusAt(offset.x, offset.y)
                        } else {
                            focusCameraAt(
                                camera = preRecordCamera,
                                previewView = preRecordPreviewView,
                                x = offset.x,
                                y = offset.y,
                            )
                        }
                    }
                }
                .pointerInput(isRecording) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (isRecording && zoom != 1f && zoom > 0f) {
                            videoRecorder.zoomDuringRecording(zoom)
                        }
                    }
                },
        )

        focusPoint?.let { point ->
            FocusRing(
                center = point,
                focused = videoRecorder.focusResult,
                modifier = Modifier.fillMaxSize(),
            )
        }

        CameraScrims()

        if (isRecording && abs(videoRecorder.liveZoomRatio - 1f) > 0.05f) {
            ZoomChip(
                ratio = videoRecorder.liveZoomRatio,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            )
        }

        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
                Text(
                    text = formatDuration(videoRecorder.recordingTime * 1000),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        if (saving) {
            Text(
                text = stringResource(R.string.camera_standard_saving),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        TopControls(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            onClose = {
                if (isRecording) {
                    stopAndSave(exitAfterwards = true)
                } else {
                    onClose()
                }
            },
            onOpenSettings = onOpenVideoSettings,
            onSwitchLens = if (!isRecording && canSwitchLens) {
                {
                    videoRecorder.cameraID =
                        if (videoRecorder.cameraID == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                }
            } else {
                null
            },
            leadingExtra = {
                if (isRecording) {
                    TorchButton(recorder = videoRecorder)
                }
            },
        )

        BottomControls(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            mode = StandardCameraMode.VIDEO,
            onSelectPhoto = onSwitchToPhoto,
            onSelectVideo = {},
            modeEnabled = !isRecording && !saving,
            leading = {
                if (isRecording) {
                    IconButton(
                        onClick = {
                            if (videoRecorder.isPaused) {
                                videoRecorder.resumeRecording()
                            } else {
                                videoRecorder.pauseRecording()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.16f), CircleShape),
                    ) {
                        Icon(
                            if (videoRecorder.isPaused) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.Pause
                            },
                            contentDescription = stringResource(
                                if (videoRecorder.isPaused) {
                                    R.string.camera_standard_resume_recording
                                } else {
                                    R.string.camera_standard_pause_recording
                                }
                            ),
                            tint = Color.White,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canSwitchLens) {
                            IconButton(
                                onClick = {
                                    videoRecorder.cameraID =
                                        if (videoRecorder.cameraID ==
                                            CameraSelector.LENS_FACING_BACK
                                        ) {
                                            CameraSelector.LENS_FACING_FRONT
                                        } else {
                                            CameraSelector.LENS_FACING_BACK
                                        }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Cameraswitch,
                                    contentDescription = stringResource(
                                        R.string.camera_standard_switch_lens
                                    ),
                                    tint = Color.White,
                                )
                            }
                        }
                        PreRecordTorchButton(camera = preRecordCamera)
                    }
                }
            },
            primaryAction = {
                if (isRecording) {
                    RecordStopButton(
                        enabled = !saving,
                        onClick = { stopAndSave(exitAfterwards = false) },
                    )
                } else {
                    RecordButton(
                        enabled = !pendingRecording,
                        onClick = { pendingRecording = true },
                    )
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ */
/* Shared controls                                                     */
/* ------------------------------------------------------------------ */

/**
 * System cameras put a subtle dark gradient behind the top/bottom overlays so
 * white controls stay readable on bright camera content.
 */
@Composable
private fun BoxScope.CameraScrims() {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(150.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                )
            ),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(260.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                )
            ),
    )
}

@Composable
private fun TopControls(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchLens: (() -> Unit)? = null,
    leadingExtra: @Composable () -> Unit = {},
    trailingExtra: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.camera_standard_close),
                    tint = Color.White,
                )
            }
            leadingExtra()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            trailingExtra()
            if (onSwitchLens != null) {
                IconButton(onClick = onSwitchLens) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.camera_standard_switch_lens),
                        tint = Color.White,
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.ui_settings_title),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun FlashModeButton(
    mode: PhotoFlashMode,
    onCycle: () -> Unit,
) {
    val (icon, description) = when (mode) {
        PhotoFlashMode.OFF -> Icons.Default.FlashOff to R.string.camera_standard_flash_off
        PhotoFlashMode.AUTO -> Icons.Default.FlashAuto to R.string.camera_standard_flash_auto
        PhotoFlashMode.ON -> Icons.Default.FlashOn to R.string.camera_standard_flash_on
    }
    IconButton(onClick = onCycle) {
        Icon(
            icon,
            contentDescription = stringResource(description),
            tint = Color.White,
        )
    }
}

@Composable
private fun TorchButton(recorder: VideoRecorderModel) {
    val cameraControl = recorder.recorderService?.cameraControl
    val available = cameraControl?.hasTorchAvailable() == true
    var enabled by remember(cameraControl) {
        mutableStateOf(cameraControl?.torchEnabled == true)
    }
    if (available) {
        IconButton(
            onClick = {
                enabled = !enabled
                if (enabled) {
                    cameraControl?.enableTorch()
                } else {
                    cameraControl?.disableTorch()
                }
            },
        ) {
            Icon(
                if (enabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = stringResource(
                    if (enabled) {
                        R.string.camera_standard_torch_off
                    } else {
                        R.string.camera_standard_torch_on
                    }
                ),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun PreRecordTorchButton(camera: Camera?) {
    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
    var enabled by remember(camera) { mutableStateOf(false) }
    if (hasFlash) {
        IconButton(
            onClick = {
                enabled = !enabled
                runCatching { camera?.cameraControl?.enableTorch(enabled) }
            },
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.16f), CircleShape),
        ) {
            Icon(
                if (enabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = stringResource(
                    if (enabled) {
                        R.string.camera_standard_torch_off
                    } else {
                        R.string.camera_standard_torch_on
                    }
                ),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun BottomControls(
    modifier: Modifier = Modifier,
    mode: StandardCameraMode,
    onSelectPhoto: () -> Unit,
    onSelectVideo: () -> Unit,
    leading: @Composable () -> Unit,
    primaryAction: @Composable () -> Unit,
    modeEnabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeTab(
                label = stringResource(R.string.camera_standard_mode_photo),
                selected = mode == StandardCameraMode.PHOTO,
                enabled = modeEnabled,
                onClick = onSelectPhoto,
            )
            ModeTab(
                label = stringResource(R.string.camera_standard_mode_video),
                selected = mode == StandardCameraMode.VIDEO,
                enabled = modeEnabled,
                onClick = onSelectVideo,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                leading()
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                primaryAction()
            }
            Box(modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = when {
            selected -> Color.Black
            enabled -> Color.White
            else -> Color.White.copy(alpha = 0.45f)
        },
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(
                if (selected) Color.White else Color.Transparent,
                RoundedCornerShape(50),
            )
            .clickable(enabled = enabled || selected, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun PrimaryShutter(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(
                width = 4.dp,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                shape = CircleShape,
            )
            .padding(7.dp)
            .background(
                color = Color.White.copy(alpha = if (busy) 0.45f else if (enabled) 1f else 0.4f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RecordButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .padding(8.dp)
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.4f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RecordStopButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .padding(8.dp)
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.4f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Stop,
            contentDescription = stringResource(R.string.camera_standard_stop_recording),
            tint = Color.White,
        )
    }
}

@Composable
private fun FocusRing(
    center: Offset,
    focused: Boolean?,
    modifier: Modifier = Modifier,
) {
    val color = when (focused) {
        true -> Color(0xFF7BE495)
        false -> Color(0xFFFFB4A9)
        null -> Color.White.copy(alpha = 0.85f)
    }
    Canvas(modifier = modifier) {
        drawCircle(
            color = color,
            radius = 36.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun ZoomChip(
    ratio: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.photo_overlay_zoom_value, ratio),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun CameraGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = Color.White.copy(alpha = 0.28f)
        val stroke = 1.dp.toPx()
        for (index in 1..2) {
            val fraction = index / 3f
            drawLine(
                color = color,
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
                strokeWidth = stroke,
            )
            drawLine(
                color = color,
                start = Offset(0f, size.height * fraction),
                end = Offset(size.width, size.height * fraction),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.camera_standard_close),
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_standard_permission),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.camera_standard_take_photo),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

private fun focusCameraAt(
    camera: Camera?,
    previewView: PreviewView?,
    x: Float,
    y: Float,
) {
    val target = camera ?: return
    val factory = previewView?.meteringPointFactory ?: return
    runCatching {
        target.cameraControl.startFocusAndMetering(
            FocusMeteringAction
                .Builder(
                    factory.createPoint(x, y),
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
        )
    }
}

