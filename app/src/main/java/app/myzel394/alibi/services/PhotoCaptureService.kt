package app.myzel394.alibi.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.util.Range
import android.view.Display
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import app.myzel394.alibi.MainActivity
import app.myzel394.alibi.NotificationHelper
import app.myzel394.alibi.R
import app.myzel394.alibi.BuildConfig
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.PhotoAspectRatio
import app.myzel394.alibi.db.PhotoCaptureSettings
import app.myzel394.alibi.db.PhotoCaptureStrategy
import app.myzel394.alibi.db.PhotoFlashMode
import app.myzel394.alibi.db.PhotoHapticFeedback
import app.myzel394.alibi.db.PhotoResolutionPreference
import app.myzel394.alibi.helpers.PhotoNaming
import app.myzel394.alibi.helpers.PendingPhoto
import app.myzel394.alibi.helpers.PhotoRecord
import app.myzel394.alibi.helpers.PhotoRepository
import app.myzel394.alibi.helpers.PhotoStorage
import app.myzel394.alibi.ui.utils.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs

@androidx.annotation.OptIn(
    markerClass = [
        ExperimentalCamera2Interop::class,
        ExperimentalZeroShutterLag::class,
    ]
)
class PhotoCaptureService : LifecycleService() {
    private data class SessionConfig(
        val id: String,
        val mode: PhotoCaptureMode,
        val lensFacing: Int,
        val saveFolder: String?,
        val photoSettings: PhotoCaptureSettings,
    )

    private data class FastCaptureProfile(
        val useZeroShutterLag: Boolean,
        val frameRateRange: Range<Int>?,
        val useOpticalStabilization: Boolean,
        val useContinuousAutoFocus: Boolean,
    )

    private data class FailedSave(
        val requestId: Long,
        val pendingId: String,
        val temporaryFile: java.io.File,
        val displayName: String,
        val config: SessionConfig,
    )

    private data class CapturedPhoto(
        val requestId: Long,
        val pendingId: String,
        val temporaryFile: File,
        val byteCount: Long,
        val displayName: String,
        val config: SessionConfig,
    )

    private val binder = PhotoBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { PhotoRepository(this) }
    private val storage by lazy { PhotoStorage(this) }
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val overlay by lazy {
        PhotoOverlayController(
            context = this,
            onCapture = ::requestCapture,
            onOpenApp = ::openApp,
            onStop = ::stopSession,
            onZoomBy = ::zoomBy,
            onZoomTo = ::setZoomRatio,
        )
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                stopSession()
            }
        }
    }

    private var session: SessionConfig? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var appVisible = true
    private var isStopping = false
    private var stopFinalized = false
    private var foregroundStarted = false
    private var screenReceiverRegistered = false
    private val failedSaves = LinkedHashMap<String, FailedSave>()
    private val requestBudget = PhotoCaptureRequestBudget()
    private var nextRequestId = 0L
    private val captureQueue = java.util.ArrayDeque<Long>()
    private val saveQueue = java.util.ArrayDeque<CapturedPhoto>()
    private val requestStartedAtNanos = mutableMapOf<Long, Long>()
    private val hapticDeliveredRequests = mutableSetOf<Long>()
    private var captureInFlight = false
    private val zoomRequests = ZoomRequestQueue()
    private var lastZoomInputAtMillis = 0L
    private var zoomRememberJob: Job? = null
    internal var zoomInputCount = 0
        private set
    internal var zoomApplyCount = 0
        private set
    internal var zoomCoalescedCount = 0
        private set
    internal var lastZoomStalenessMillis = 0L
        private set
    private var saveWorkerRunning = false
    private var lastSavedPhotoUri: String? = null
    private var initializationJob: Job? = null
    private var saveWorkerJob: Job? = null
    private var stopTimeoutJob: Job? = null
    private var recoveryJob: Job? = null

    var onStateChanged: ((PhotoCaptureSessionState) -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null

    var state = PhotoCaptureSessionState()
        private set

    override fun onCreate() {
        super.onCreate()
        registerScreenReceiver()
        startPendingRecovery()
    }

    private fun startPendingRecovery() {
        if (recoveryJob?.isActive == true) {
            return
        }
        recoveryJob = scope.launch {
            try {
                for (pending in repository.recoverPendingFiles()) {
                    recoverPhoto(pending)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not recover pending photos", error)
            } finally {
                recoveryJob = null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForegroundNotification()
                startSession(intent)
            }

            ACTION_CAPTURE -> requestCapture()
            ACTION_STOP -> stopSession()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        releaseCamera()
        overlay.hide()
        initializationJob?.cancel()
        saveWorkerJob?.cancel()
        stopTimeoutJob?.cancel()
        recoveryJob?.cancel()
        cameraExecutor.shutdownNow()
        scope.cancel()
        super.onDestroy()
    }

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        updateOverlay()
    }

    fun setMode(mode: PhotoCaptureMode) {
        val currentSession = session ?: return

        if (state.status !in listOf(
                PhotoCaptureStatus.STARTING,
                PhotoCaptureStatus.READY,
                PhotoCaptureStatus.CAPTURING,
                PhotoCaptureStatus.SAVING,
            )
        ) {
            return
        }

        if (currentSession.mode == mode) {
            updateOverlay()
            return
        }

        session = currentSession.copy(mode = mode)
        publish(state.copy(mode = mode, error = null))
        updateOverlay()
    }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = provider
        preview?.setSurfaceProvider(provider)
    }

    fun zoomBy(scaleFactor: Float) {
        if (scaleFactor <= 0f) {
            return
        }
        requestZoom(state.zoomRatio * scaleFactor)
    }

    fun setZoomRatio(target: Float) {
        requestZoom(target)
    }

    private fun requestZoom(target: Float) {
        if (!state.isActive || isStopping) {
            return
        }

        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val clamped = target.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        if (abs(clamped - state.zoomRatio) < ZOOM_EPSILON) {
            return
        }

        // Update the session state optimistically (without publishing: zoom-only
        // changes must not recompose the whole screen or touch overlay windows).
        state = state.copy(zoomRatio = clamped)
        lastZoomInputAtMillis = SystemClock.elapsedRealtime()
        zoomInputCount++
        onZoomChanged?.invoke(clamped)
        updateOverlayZoom()
        rememberFloatingZoom(clamped)

        val immediate = zoomRequests.submit(clamped)
        if (immediate != null) {
            applyZoomRequest(immediate)
        } else {
            zoomCoalescedCount++
        }
    }

    /**
     * Applies at most one CameraX zoom request at a time and drops intermediate
     * ratios, so a continuous pinch can never build a request backlog.
     */
    private fun applyZoomRequest(target: Float) {
        val control = camera?.cameraControl
        if (control == null) {
            zoomRequests.reset()
            return
        }
        lastZoomStalenessMillis =
            (SystemClock.elapsedRealtime() - lastZoomInputAtMillis).coerceAtLeast(0L)
        val startedAt = SystemClock.elapsedRealtime()
        val future = runCatching { control.setZoomRatio(target) }.getOrNull()
        if (future == null) {
            applyNextZoomRequest()
            return
        }
        zoomApplyCount++
        future.addListener(
            {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "zoom applied target=$target latencyMs=" +
                            (SystemClock.elapsedRealtime() - startedAt),
                    )
                }
                applyNextZoomRequest()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun applyNextZoomRequest() {
        val next = zoomRequests.complete()
        if (next != null) {
            applyZoomRequest(next)
        }
    }

    private fun updateOverlayZoom() {
        val currentSession = session ?: return
        if (currentSession.mode != PhotoCaptureMode.FLOATING || appVisible || isStopping) {
            return
        }
        val zoomState = camera?.cameraInfo?.zoomState?.value
        overlay.updateZoom(
            ratio = state.zoomRatio,
            minRatio = zoomState?.minZoomRatio ?: 1f,
            maxRatio = zoomState?.maxZoomRatio ?: 1f,
        )
    }

    /** Floating windows have no black screen UI, so they persist their zoom themselves. */
    private fun rememberFloatingZoom(ratio: Float) {
        val currentSession = session ?: return
        if (currentSession.mode != PhotoCaptureMode.FLOATING ||
            !currentSession.photoSettings.rememberZoom
        ) {
            return
        }
        zoomRememberJob?.cancel()
        zoomRememberJob = scope.launch {
            delay(ZOOM_REMEMBER_DELAY_MILLIS)
            runCatching {
                dataStore.updateData { settings ->
                    settings.setPhotoCaptureSettings(
                        settings.photoCaptureSettings.setLastZoomRatio(ratio)
                    )
                }
            }.onFailure { Log.w(TAG, "Could not remember overlay zoom", it) }
        }
    }

    fun requestCapture() {
        val currentSession = session ?: return

        if (!state.canAcceptCapture ||
            isStopping ||
            !requestBudget.canAccept
        ) {
            return
        }

        val requestId = ++nextRequestId
        if (!requestBudget.accept(requestId)) {
            return
        }
        requestStartedAtNanos[requestId] = SystemClock.elapsedRealtimeNanos()
        captureQueue.addLast(requestId)
        publish(
            state.copy(
                status = PhotoCaptureStatus.CAPTURING,
                mode = currentSession.mode,
                error = null,
                canRetrySave = false,
                queuedCaptures = captureQueue.size,
            )
        )

        if (!captureInFlight) {
            startNextCapture()
        }
    }

    private fun startNextCapture() {
        if (isStopping || captureQueue.isEmpty()) {
            captureInFlight = false
            publishCaptureIdleState()
            return
        }

        val currentSession = session ?: run {
            discardQueuedRequests()
            captureInFlight = false
            publishCaptureIdleState()
            return
        }
        val capture = imageCapture ?: run {
            discardQueuedRequests()
            captureInFlight = false
            publishError(IllegalStateException("The camera is not ready"))
            return
        }

        val requestId = captureQueue.removeFirst()
        captureInFlight = true
        publish(
            state.copy(
                status = PhotoCaptureStatus.CAPTURING,
                mode = currentSession.mode,
                queuedCaptures = captureQueue.size,
                error = null,
            )
        )

        scope.launch {
            prepareAndStartCapture(requestId, currentSession, capture)
        }
    }

    private suspend fun prepareAndStartCapture(
        requestId: Long,
        currentSession: SessionConfig,
        capture: ImageCapture,
    ) {
        val pendingId = PhotoRepository.pendingId()
        val displayName = PhotoNaming.create(
            LocalDateTime.now(),
            UUID.randomUUID().toString(),
        )
        val temporaryFile = try {
            repository.newTemporaryFile()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleCaptureFailure(requestId, currentSession, null, null, error)
            return
        }

        if (!isCurrentSession(currentSession.id)) {
            temporaryFile.delete()
            return
        }

        try {
            repository.markPending(
                PhotoRepository.pendingPhoto(
                    id = pendingId,
                    temporaryFile = temporaryFile,
                    lensFacing = currentSession.lensFacing,
                    displayName = displayName,
                    saveFolder = currentSession.saveFolder,
                ),
            )
        } catch (error: CancellationException) {
            temporaryFile.delete()
            throw error
        } catch (error: Exception) {
            temporaryFile.delete()
            handleCaptureFailure(requestId, currentSession, null, pendingId, error)
            return
        }

        if (!isCurrentSession(currentSession.id)) {
            cleanupPendingPhoto(pendingId, temporaryFile)
            return
        }

        capture.targetRotation = currentDisplayRotation()
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = currentSession.lensFacing ==
                CameraSelector.LENS_FACING_FRONT &&
                currentSession.photoSettings.mirrorFrontCamera
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(temporaryFile)
            .setMetadata(metadata)
            .build()

        try {
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!isCurrentSession(currentSession.id)) {
                            cleanupPendingPhoto(pendingId, temporaryFile)
                            return
                        }

                        captureInFlight = false
                        val latencyMillis = requestStartedAtNanos[requestId]?.let {
                            (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000L
                        }
                        Log.i(
                            TAG,
                            "Photo captured: request=$requestId latencyMs=$latencyMillis " +
                                "queued=${captureQueue.size}",
                        )
                        if (hapticDeliveredRequests.add(requestId)) {
                            vibrateAfterCapture(currentSession)
                        }
                        val byteCount = temporaryFile.length().coerceAtLeast(0L)
                        requestBudget.addCapturedBytes(requestId, byteCount)
                        saveQueue.addLast(
                            CapturedPhoto(
                                requestId = requestId,
                                pendingId = pendingId,
                                temporaryFile = temporaryFile,
                                byteCount = byteCount,
                                displayName = displayName,
                                config = currentSession,
                            ),
                        )
                        startSaveWorker()
                        startNextCapture()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed", exception)
                        handleCaptureFailure(
                            requestId,
                            currentSession,
                            temporaryFile,
                            pendingId,
                            exception,
                        )
                    }
                },
            )
        } catch (error: Exception) {
            handleCaptureFailure(
                requestId,
                currentSession,
                temporaryFile,
                pendingId,
                error,
            )
        }
    }

    private fun handleCaptureFailure(
        requestId: Long,
        currentSession: SessionConfig,
        temporaryFile: File?,
        pendingId: String?,
        error: Throwable,
    ) {
        if (!isCurrentSession(currentSession.id)) {
            temporaryFile?.let { cleanupPendingPhoto(pendingId, it) }
            return
        }

        captureInFlight = false
        discardQueuedRequests()
        finishRequest(requestId)
        temporaryFile?.let {
            cleanupPendingPhoto(pendingId, it)
        }
        if (isStopping) {
            maybeFinalizeStop()
        } else {
            publishError(error)
        }
    }

    private fun isCurrentSession(sessionId: String): Boolean {
        return session?.id == sessionId && !stopFinalized
    }

    private fun finishRequest(requestId: Long) {
        requestStartedAtNanos.remove(requestId)
        requestBudget.finish(requestId)
        publishCaptureIdleState()
    }

    private fun discardQueuedRequests() {
        while (captureQueue.isNotEmpty()) {
            val requestId = captureQueue.removeFirst()
            requestStartedAtNanos.remove(requestId)
            requestBudget.finish(requestId)
        }
    }

    private fun cleanupPendingPhoto(pendingId: String?, temporaryFile: File) {
        scope.launch {
            try {
                if (pendingId != null) {
                    repository.removePending(pendingId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not remove an abandoned pending photo", error)
            } finally {
                temporaryFile.delete()
            }
        }
    }

    fun retryLastSave() {
        if (failedSaves.isEmpty() ||
            state.status != PhotoCaptureStatus.ERROR ||
            isStopping ||
            saveWorkerRunning
        ) {
            return
        }

        val retries = failedSaves.values.filter { it.temporaryFile.isFile }
        if (retries.isEmpty()) {
            publishError(IllegalStateException("The temporary photo is no longer available"))
            return
        }

        publish(
            state.copy(
                status = PhotoCaptureStatus.SAVING,
                mode = retries.last().config.mode,
                error = null,
                canRetrySave = false,
            )
        )
        retries.forEach { retry ->
            saveQueue.addLast(
                CapturedPhoto(
                    requestId = retry.requestId,
                    pendingId = retry.pendingId,
                    temporaryFile = retry.temporaryFile,
                    byteCount = retry.temporaryFile.length().coerceAtLeast(0L),
                    displayName = retry.displayName,
                    config = retry.config,
                )
            )
        }
        startSaveWorker()
    }

    fun stopSession() {
        if ((session == null && !state.isActive) || isStopping || stopFinalized) {
            return
        }

        isStopping = true
        discardQueuedRequests()
        publish(state.copy(status = PhotoCaptureStatus.STOPPING, error = null))
        overlay.hide()

        if (captureInFlight || saveWorkerRunning || saveQueue.isNotEmpty()) {
            stopTimeoutJob?.cancel()
            val sessionId = session?.id
            stopTimeoutJob = scope.launch {
                delay(STOP_TIMEOUT_MILLIS)
                if (sessionId != null && isCurrentSession(sessionId) && isStopping) {
                    finalizeStop(sessionId)
                }
            }
            return
        }

        finalizeStop(session?.id)
    }

    private fun finalizeStop(expectedSessionId: String?) {
        if (stopFinalized ||
            expectedSessionId == null ||
            session?.id != expectedSessionId
        ) {
            return
        }
        stopFinalized = true
        initializationJob?.cancel()
        initializationJob = null
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        saveWorkerJob?.cancel()
        saveWorkerJob = null
        captureQueue.clear()
        saveQueue.clear()
        requestBudget.clear()
        requestStartedAtNanos.clear()
        hapticDeliveredRequests.clear()
        failedSaves.clear()
        captureInFlight = false
        saveWorkerRunning = false
        lastSavedPhotoUri = null
        releaseCamera()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        session = null
        stopSelf()
        publish(PhotoCaptureSessionState())
        isStopping = false
        startPendingRecovery()
    }

    private fun startSession(intent: Intent) {
        val mode = runCatching {
            PhotoCaptureMode.valueOf(
                intent.getStringExtra(EXTRA_MODE) ?: PhotoCaptureMode.BLACK_SCREEN.name
            )
        }.getOrDefault(PhotoCaptureMode.BLACK_SCREEN)
        if (intent.getBooleanExtra(EXTRA_START_IN_BACKGROUND, false)) {
            appVisible = false
        }
        zoomRequests.reset()
        zoomInputCount = 0
        zoomApplyCount = 0
        zoomCoalescedCount = 0
        lastZoomStalenessMillis = 0L
        if (session != null || state.isActive) {
            setMode(mode)
            updateOverlay()
            return
        }
        val sessionConfig = SessionConfig(
            id = UUID.randomUUID().toString(),
            mode = mode,
            lensFacing = intent.getIntExtra(
                EXTRA_LENS_FACING,
                CameraSelector.LENS_FACING_BACK,
            ),
            saveFolder = if (intent.hasExtra(EXTRA_SAVE_FOLDER)) {
                intent.getStringExtra(EXTRA_SAVE_FOLDER)
            } else {
                null
            },
            photoSettings = PhotoCaptureSettings(
                feedbackEnabled = intent.getBooleanExtra(
                    EXTRA_FEEDBACK_ENABLED,
                    true,
                ),
                fastCaptureEnabled = intent.getBooleanExtra(
                    EXTRA_FAST_CAPTURE_ENABLED,
                    true,
                ),
                lensFacing = intent.getIntExtra(
                    EXTRA_LENS_FACING,
                    CameraSelector.LENS_FACING_BACK,
                ),
                captureStrategy = runCatching {
                    PhotoCaptureStrategy.valueOf(
                        intent.getStringExtra(EXTRA_CAPTURE_STRATEGY)
                            ?: PhotoCaptureStrategy.FAST.name
                    )
                }.getOrDefault(PhotoCaptureStrategy.FAST),
                hapticFeedback = runCatching {
                    PhotoHapticFeedback.valueOf(
                        intent.getStringExtra(EXTRA_HAPTIC_FEEDBACK)
                            ?: PhotoHapticFeedback.STANDARD.name
                    )
                }.getOrDefault(PhotoHapticFeedback.STANDARD),
                resolutionPreference = runCatching {
                    PhotoResolutionPreference.valueOf(
                        intent.getStringExtra(EXTRA_RESOLUTION_PREFERENCE)
                            ?: PhotoResolutionPreference.RECOMMENDED.name
                    )
                }.getOrDefault(PhotoResolutionPreference.RECOMMENDED),
                aspectRatio = runCatching {
                    PhotoAspectRatio.valueOf(
                        intent.getStringExtra(EXTRA_ASPECT_RATIO)
                            ?: PhotoAspectRatio.FOUR_THREE.name
                    )
                }.getOrDefault(PhotoAspectRatio.FOUR_THREE),
                flashMode = runCatching {
                    PhotoFlashMode.valueOf(
                        intent.getStringExtra(EXTRA_FLASH_MODE)
                            ?: PhotoFlashMode.OFF.name
                    )
                }.getOrDefault(PhotoFlashMode.OFF),
                opticalStabilizationEnabled = intent.getBooleanExtra(
                    EXTRA_OPTICAL_STABILIZATION,
                    true,
                ),
                continuousAutoFocusEnabled = intent.getBooleanExtra(
                    EXTRA_CONTINUOUS_AUTO_FOCUS,
                    true,
                ),
                mirrorFrontCamera = intent.getBooleanExtra(
                    EXTRA_MIRROR_FRONT_CAMERA,
                    true,
                ),
                showPreviewOnLaunch = intent.getBooleanExtra(
                    EXTRA_SHOW_PREVIEW_ON_LAUNCH,
                    false,
                ),
                overlayOpacity = intent.getFloatExtra(
                    EXTRA_OVERLAY_OPACITY,
                    0.9f,
                ),
                lastZoomRatio = intent.getFloatExtra(
                    EXTRA_INITIAL_ZOOM,
                    1f,
                ),
                rememberZoom = intent.getBooleanExtra(
                    EXTRA_REMEMBER_ZOOM,
                    false,
                ),
            ),
        )
        initializationJob?.cancel()
        stopTimeoutJob?.cancel()
        saveWorkerJob?.cancel()
        session = sessionConfig
        isStopping = false
        stopFinalized = false
        failedSaves.clear()
        requestBudget.clear()
        hapticDeliveredRequests.clear()
        captureQueue.clear()
        saveQueue.clear()
        captureInFlight = false
        saveWorkerRunning = false
        lastSavedPhotoUri = null
        publish(
            PhotoCaptureSessionState(
                status = PhotoCaptureStatus.STARTING,
                mode = mode,
            )
        )

        initializationJob = scope.launch {
            try {
                val provider = withContext(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(this@PhotoCaptureService).get()
                }
                if (!isCurrentSession(sessionConfig.id) || isStopping) {
                    return@launch
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(sessionConfig.lensFacing)
                    .build()

                val cameraInfo = selector
                    .filter(provider.availableCameraInfos)
                    .firstOrNull()
                    ?: error("The selected camera is not available")
                val fastCaptureProfile = buildFastCaptureProfile(
                    cameraInfo = cameraInfo,
                    settings = sessionConfig.photoSettings,
                )
                if (fastCaptureProfile.useZeroShutterLag ||
                    fastCaptureProfile.frameRateRange != null
                ) {
                    Log.i(
                        TAG,
                        "Capture profile ${sessionConfig.photoSettings.getEffectiveCaptureStrategy()}: " +
                            "ZSL=${fastCaptureProfile.useZeroShutterLag}, " +
                            "fps=${fastCaptureProfile.frameRateRange}, " +
                            "OIS=${fastCaptureProfile.useOpticalStabilization}, " +
                            "CAF=${fastCaptureProfile.useContinuousAutoFocus}",
                    )
                }

                if (!isCurrentSession(sessionConfig.id) || isStopping) {
                    return@launch
                }
                releaseCamera()
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(
                        if (fastCaptureProfile.useZeroShutterLag) {
                            ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                        } else {
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        }
                    )
                    .setFlashMode(
                        when (sessionConfig.photoSettings.flashMode) {
                            PhotoFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                            PhotoFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                            PhotoFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                        }
                    )
                    .setResolutionSelector(
                        captureResolutionSelector(sessionConfig.photoSettings)
                    )
                    .setTargetRotation(currentDisplayRotation())
                applyFastCaptureOptions(
                    Camera2Interop.Extender<ImageCapture>(captureBuilder),
                    fastCaptureProfile,
                )
                val capture = captureBuilder.build()

                val previewUseCase = Preview.Builder()
                    .setTargetRotation(currentDisplayRotation())
                    .build()
                val analysisBuilder = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                applyFastCaptureOptions(
                    Camera2Interop.Extender<ImageAnalysis>(analysisBuilder),
                    fastCaptureProfile,
                )
                val analysis = analysisBuilder
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(cameraExecutor) { image ->
                            image.close()
                        }
                    }
                if (!isCurrentSession(sessionConfig.id) || isStopping) {
                    analysis.clearAnalyzer()
                    return@launch
                }
                cameraProvider = provider
                imageCapture = capture
                imageAnalysis = analysis
                preview = previewUseCase
                previewUseCase.setSurfaceProvider(previewSurfaceProvider)
                camera = try {
                    provider.bindToLifecycle(
                        this@PhotoCaptureService,
                        selector,
                        previewUseCase,
                        capture,
                        analysis,
                    )
                } catch (error: IllegalArgumentException) {
                    Log.w(
                        TAG,
                        "Fast photo use-case combination is unavailable; retrying without analysis",
                        error,
                    )
                    analysis.clearAnalyzer()
                    val fallbackBuilder = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(
                            when (sessionConfig.photoSettings.flashMode) {
                                PhotoFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                                PhotoFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                PhotoFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                            }
                        )
                        .setResolutionSelector(
                            captureResolutionSelector(sessionConfig.photoSettings)
                        )
                        .setTargetRotation(currentDisplayRotation())
                    applyFastCaptureOptions(
                        Camera2Interop.Extender<ImageCapture>(fallbackBuilder),
                        fastCaptureProfile,
                    )
                    val fallbackCapture = fallbackBuilder.build()
                    imageCapture = fallbackCapture
                    provider.bindToLifecycle(
                        this@PhotoCaptureService,
                        selector,
                        previewUseCase,
                        fallbackCapture,
                    )
                }
                if (!isCurrentSession(sessionConfig.id) || isStopping) {
                    releaseCamera()
                    return@launch
                }
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val initialZoom = sessionConfig.photoSettings.lastZoomRatio.coerceIn(
                    zoomState?.minZoomRatio ?: 1f,
                    zoomState?.maxZoomRatio ?: 1f,
                )
                if (initialZoom != 1f) {
                    camera?.cameraControl?.setZoomRatio(initialZoom)
                }
                publish(
                    state.copy(
                        status = PhotoCaptureStatus.READY,
                        mode = session?.mode ?: sessionConfig.mode,
                        error = null,
                        zoomRatio = initialZoom,
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not initialize photo capture", error)
                if (isCurrentSession(sessionConfig.id)) {
                    publishError(error)
                }
            }
        }
    }

    private fun buildFastCaptureProfile(
        cameraInfo: CameraInfo,
        settings: PhotoCaptureSettings,
    ): FastCaptureProfile {
        val strategy = settings.getEffectiveCaptureStrategy()
        val useFastOptions = strategy != PhotoCaptureStrategy.BALANCED
        val zeroShutterLagSupported =
            useFastOptions && settings.flashMode == PhotoFlashMode.OFF && runCatching {
                cameraInfo.isZslSupported()
            }.getOrDefault(false)
        val fastFrameRateRange = if (useFastOptions) {
            val candidates = cameraInfo.supportedFrameRateRanges
                .filter { it.lower >= MIN_FAST_CAPTURE_FPS }
            val targetUpper = if (strategy == PhotoCaptureStrategy.MOTION) {
                MOTION_CAPTURE_FPS
            } else {
                MIN_FAST_CAPTURE_FPS
            }
            candidates.minWithOrNull(
                compareBy<Range<Int>> { abs(it.upper - targetUpper) }
                    .thenBy { it.lower }
                    .thenBy { it.upper }
            )
        } else {
            null
        }
        val opticalStabilizationSupported = runCatching {
            Camera2CameraInfo
                .from(cameraInfo)
                .getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
                )
                ?.contains(
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                ) == true
        }.getOrDefault(false)
        val continuousAutoFocusSupported = runCatching {
            Camera2CameraInfo
                .from(cameraInfo)
                .getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
                )
                ?.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true
        }.getOrDefault(false)

        return FastCaptureProfile(
            useZeroShutterLag = zeroShutterLagSupported,
            frameRateRange = fastFrameRateRange,
            useOpticalStabilization = settings.opticalStabilizationEnabled &&
                opticalStabilizationSupported,
            useContinuousAutoFocus = settings.continuousAutoFocusEnabled &&
                continuousAutoFocusSupported,
        )
    }

    private fun applyFastCaptureOptions(
        builder: Camera2Interop.Extender<*>,
        profile: FastCaptureProfile,
    ) {
        profile.frameRateRange?.let { range ->
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                range,
            )
        }
        if (profile.useOpticalStabilization) {
            builder.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON,
            )
        }
        if (profile.useContinuousAutoFocus) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
        }
    }

    private fun captureResolutionSelector(settings: PhotoCaptureSettings): ResolutionSelector {
        val aspectRatio = when (settings.aspectRatio) {
            PhotoAspectRatio.FOUR_THREE -> AspectRatio.RATIO_4_3
            PhotoAspectRatio.SIXTEEN_NINE -> AspectRatio.RATIO_16_9
        }
        val target = when (settings.resolutionPreference) {
            PhotoResolutionPreference.SPEED -> if (
                settings.aspectRatio == PhotoAspectRatio.FOUR_THREE
            ) {
                Size(1280, 960)
            } else {
                Size(1280, 720)
            }
            PhotoResolutionPreference.RECOMMENDED -> if (
                settings.aspectRatio == PhotoAspectRatio.FOUR_THREE
            ) {
                Size(1920, 1440)
            } else {
                Size(1920, 1080)
            }
            PhotoResolutionPreference.QUALITY -> if (
                settings.aspectRatio == PhotoAspectRatio.FOUR_THREE
            ) {
                Size(4032, 3024)
            } else {
                Size(3840, 2160)
            }
        }
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    aspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO,
                )
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    target,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()
    }

    private fun startSaveWorker() {
        if (saveWorkerRunning || saveQueue.isEmpty()) {
            return
        }

        saveWorkerRunning = true
        if (!captureInFlight && captureQueue.isEmpty() && !isStopping && session != null) {
            publish(state.copy(status = PhotoCaptureStatus.SAVING))
        }

        saveWorkerJob = scope.launch {
            try {
                while (saveQueue.isNotEmpty() && !stopFinalized) {
                    val captured = saveQueue.removeFirst()
                    val saveStartedAtNanos = SystemClock.elapsedRealtimeNanos()
                    val error = performPhotoSave(captured)
                    val saveMillis = (
                        SystemClock.elapsedRealtimeNanos() - saveStartedAtNanos
                    ) / 1_000_000L

                    if (error == null) {
                        Log.i(
                            TAG,
                            "Photo saved: request=${captured.requestId} saveMs=$saveMillis",
                        )
                        failedSaves.remove(captured.pendingId)
                        requestBudget.finish(
                            captured.requestId,
                            captured.byteCount,
                        )
                        if (isCurrentSession(captured.config.id)) {
                            publishCaptureIdleState()
                        }
                    } else {
                        Log.e(TAG, "Could not save captured photo", error)
                        failedSaves[captured.pendingId] = FailedSave(
                            requestId = captured.requestId,
                            pendingId = captured.pendingId,
                            temporaryFile = captured.temporaryFile,
                            displayName = captured.displayName,
                            config = captured.config,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                saveWorkerRunning = false
                saveWorkerJob = null
                if (isStopping) {
                    maybeFinalizeStop()
                } else {
                    publishCaptureIdleState()
                }
            }
        }
    }

    private suspend fun performPhotoSave(captured: CapturedPhoto): Throwable? {
        return try {
            val stored = withContext(Dispatchers.IO) {
                storage.store(
                    temporaryFile = captured.temporaryFile,
                    saveFolder = captured.config.saveFolder,
                    displayName = captured.displayName,
                    onDestinationPrepared = { destination ->
                        repository.markDestinationPrepared(
                            captured.pendingId,
                            destination,
                        )
                    },
                )
            }
            val record = PhotoRecord(
                id = captured.pendingId,
                displayName = captured.displayName,
                uri = stored.uri.toString(),
                localPath = stored.localPath,
                createdAtEpochMillis = Instant.now().toEpochMilli(),
                lensFacing = captured.config.lensFacing,
                width = stored.width,
                height = stored.height,
            )
            repository.complete(captured.pendingId, record)
            if (isCurrentSession(captured.config.id)) {
                lastSavedPhotoUri = stored.uri.toString()
            }
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        }
    }

    private suspend fun recoverPhoto(pending: PendingPhoto) {
        val temporaryFile = File(pending.temporaryPath)
        if (!temporaryFile.isFile) {
            return
        }
        val createdAt = Instant
            .ofEpochMilli(pending.createdAtEpochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        val displayName = pending.displayName ?: PhotoNaming.create(createdAt, pending.id)
        val config = SessionConfig(
            id = "recovery-${pending.id}",
            mode = PhotoCaptureMode.BLACK_SCREEN,
            lensFacing = pending.lensFacing,
            saveFolder = pending.saveFolder,
            photoSettings = PhotoCaptureSettings(
                feedbackEnabled = false,
                fastCaptureEnabled = false,
                captureStrategy = PhotoCaptureStrategy.BALANCED,
                hapticFeedback = PhotoHapticFeedback.OFF,
            ),
        )
        val error = performPhotoSave(
            CapturedPhoto(
                requestId = RECOVERY_REQUEST_ID,
                pendingId = pending.id,
                temporaryFile = temporaryFile,
                byteCount = temporaryFile.length().coerceAtLeast(0L),
                displayName = displayName,
                config = config,
            ),
        )
        if (error != null) {
            Log.w(TAG, "Could not recover pending photo ${pending.id}", error)
        }
    }

    private fun publishCaptureIdleState() {
        if (isStopping || stopFinalized || session == null) {
            return
        }

        when {
            captureInFlight || captureQueue.isNotEmpty() -> {
                publish(
                    state.copy(
                        status = PhotoCaptureStatus.CAPTURING,
                        queuedCaptures = captureQueue.size,
                    )
                )
            }

            saveWorkerRunning || saveQueue.isNotEmpty() -> {
                publish(
                    state.copy(
                        status = PhotoCaptureStatus.SAVING,
                        queuedCaptures = 0,
                    )
                )
            }

            failedSaves.isNotEmpty() -> {
                val error = failedSaves.values.last()
                publish(
                    state.copy(
                        status = PhotoCaptureStatus.ERROR,
                        error = getString(R.string.photo_error_generic),
                        canRetrySave = true,
                        queuedCaptures = 0,
                        lastPhotoUri = lastSavedPhotoUri ?: state.lastPhotoUri,
                        mode = error.config.mode,
                    )
                )
            }

            else -> {
                publish(
                    state.copy(
                        status = PhotoCaptureStatus.READY,
                        error = null,
                        canRetrySave = false,
                        queuedCaptures = 0,
                        lastPhotoUri = lastSavedPhotoUri ?: state.lastPhotoUri,
                    )
                )
            }
        }
    }

    private fun maybeFinalizeStop() {
        if (isStopping && !captureInFlight && !saveWorkerRunning && saveQueue.isEmpty()) {
            finalizeStop(session?.id)
        }
    }

    private fun startForegroundNotification() {
        if (foregroundStarted) {
            return
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.PHOTO_NOTIFICATION_ID,
            buildNotification(),
            type,
        )
        foregroundStarted = true
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, PhotoCaptureService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NotificationHelper.PHOTO_CHANNEL_ID)
            .setSmallIcon(R.drawable.launcher_monochrome_noopacity)
            .setContentTitle(getString(R.string.photo_notification_title))
            .setContentText(getString(R.string.photo_notification_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.photo_overlay_stop),
                stopIntent,
            )
            .build()
    }

    private fun releaseCamera() {
        val provider = cameraProvider
        val previewUseCase = preview
        val capture = imageCapture
        val analysis = imageAnalysis

        previewUseCase?.setSurfaceProvider(null)
        if (provider != null) {
            runCatching {
                provider.unbind(
                    *listOfNotNull(previewUseCase, capture, analysis)
                        .toTypedArray()
                )
            }
        }
        analysis?.clearAnalyzer()
        preview = null
        imageCapture = null
        imageAnalysis = null
        camera = null
        cameraProvider = null
        zoomRequests.reset()
        zoomRememberJob?.cancel()
        zoomRememberJob = null
    }

    private fun publishError(error: Throwable) {
        if (isStopping) {
            return
        }

        val message = error.message
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.photo_error_generic)
        publish(
            state.copy(
                status = PhotoCaptureStatus.ERROR,
                error = message,
                canRetrySave = false,
            )
        )
    }

    private fun publish(newState: PhotoCaptureSessionState) {
        state = newState.copy(pendingCaptures = requestBudget.pendingCount)
        onStateChanged?.invoke(state)
        updateOverlay()
    }

    private fun updateOverlay() {
        val currentSession = session
        if (currentSession?.mode != PhotoCaptureMode.FLOATING || appVisible || isStopping) {
            overlay.hide()
            return
        }

        if (!PermissionHelper.hasGranted(this, Manifest.permission.CAMERA) ||
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            overlay.hide()
            stopSession()
            return
        }

        overlay.setOpacity(currentSession.photoSettings.overlayOpacity)
        val shown = overlay.show(
            when (state.status) {
                PhotoCaptureStatus.STARTING -> PhotoOverlayController.Status.STARTING
                PhotoCaptureStatus.READY -> PhotoOverlayController.Status.READY
                PhotoCaptureStatus.CAPTURING,
                PhotoCaptureStatus.SAVING,
                PhotoCaptureStatus.STOPPING -> PhotoOverlayController.Status.BUSY

                PhotoCaptureStatus.ERROR -> PhotoOverlayController.Status.ERROR
                PhotoCaptureStatus.STOPPED -> return
            }
        )
        if (!shown) {
            stopSession()
        } else {
            updateOverlayZoom()
        }
    }

    @SuppressLint("MissingPermission")
    private fun vibrateAfterCapture(currentSession: SessionConfig) {
        val feedback = currentSession.photoSettings.getEffectiveHapticFeedback()
        if (feedback == PhotoHapticFeedback.OFF) return
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val duration = if (feedback == PhotoHapticFeedback.LIGHT) 10L else 20L
        val amplitude = if (feedback == PhotoHapticFeedback.LIGHT) 28 else 55
        runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun currentDisplayRotation(): Int {
        val displayManager = getSystemService(DisplayManager::class.java)
        return displayManager
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
            ?: Surface.ROTATION_0
    }

    @Suppress("DEPRECATION")
    private fun registerScreenReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    inner class PhotoBinder : Binder() {
        fun getService(): PhotoCaptureService = this@PhotoCaptureService
    }

    companion object {
        private const val TAG = "PhotoCaptureService"
        val ACTION_START = "${BuildConfig.APPLICATION_ID}.photo.START"
        val ACTION_CAPTURE = "${BuildConfig.APPLICATION_ID}.photo.CAPTURE"
        val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.photo.STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LENS_FACING = "lensFacing"
        const val EXTRA_SAVE_FOLDER = "saveFolder"
        const val EXTRA_FEEDBACK_ENABLED = "feedbackEnabled"
        const val EXTRA_FAST_CAPTURE_ENABLED = "fastCaptureEnabled"
        const val EXTRA_CAPTURE_STRATEGY = "captureStrategy"
        const val EXTRA_HAPTIC_FEEDBACK = "hapticFeedback"
        const val EXTRA_RESOLUTION_PREFERENCE = "resolutionPreference"
        const val EXTRA_ASPECT_RATIO = "aspectRatio"
        const val EXTRA_FLASH_MODE = "flashMode"
        const val EXTRA_OPTICAL_STABILIZATION = "opticalStabilization"
        const val EXTRA_CONTINUOUS_AUTO_FOCUS = "continuousAutoFocus"
        const val EXTRA_MIRROR_FRONT_CAMERA = "mirrorFrontCamera"
        const val EXTRA_SHOW_PREVIEW_ON_LAUNCH = "showPreviewOnLaunch"
        const val EXTRA_OVERLAY_OPACITY = "overlayOpacity"
        const val EXTRA_INITIAL_ZOOM = "initialZoom"
        const val EXTRA_REMEMBER_ZOOM = "rememberZoom"
        const val EXTRA_START_IN_BACKGROUND = "startInBackground"
        private const val REQUEST_STOP = 41
        private const val REQUEST_OPEN = 42
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val RECOVERY_REQUEST_ID = -1L
        private const val MIN_FAST_CAPTURE_FPS = 30
        private const val MOTION_CAPTURE_FPS = 60
        private const val ZOOM_EPSILON = 0.005f
        private const val ZOOM_REMEMBER_DELAY_MILLIS = 400L

        fun createStartIntent(
            context: Context,
            settings: AppSettings,
            mode: PhotoCaptureMode,
            startInBackground: Boolean = false,
        ): Intent {
            return Intent(context, PhotoCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_LENS_FACING, settings.photoCaptureSettings.lensFacing)
                putExtra(
                    EXTRA_FEEDBACK_ENABLED,
                    settings.photoCaptureSettings.feedbackEnabled,
                )
                putExtra(
                    EXTRA_FAST_CAPTURE_ENABLED,
                    settings.photoCaptureSettings.fastCaptureEnabled,
                )
                putExtra(
                    EXTRA_CAPTURE_STRATEGY,
                    settings.photoCaptureSettings.getEffectiveCaptureStrategy().name,
                )
                putExtra(
                    EXTRA_HAPTIC_FEEDBACK,
                    settings.photoCaptureSettings.getEffectiveHapticFeedback().name,
                )
                putExtra(
                    EXTRA_RESOLUTION_PREFERENCE,
                    settings.photoCaptureSettings.resolutionPreference.name,
                )
                putExtra(EXTRA_ASPECT_RATIO, settings.photoCaptureSettings.aspectRatio.name)
                putExtra(EXTRA_FLASH_MODE, settings.photoCaptureSettings.flashMode.name)
                putExtra(
                    EXTRA_OPTICAL_STABILIZATION,
                    settings.photoCaptureSettings.opticalStabilizationEnabled,
                )
                putExtra(
                    EXTRA_CONTINUOUS_AUTO_FOCUS,
                    settings.photoCaptureSettings.continuousAutoFocusEnabled,
                )
                putExtra(
                    EXTRA_MIRROR_FRONT_CAMERA,
                    settings.photoCaptureSettings.mirrorFrontCamera,
                )
                putExtra(
                    EXTRA_SHOW_PREVIEW_ON_LAUNCH,
                    settings.photoCaptureSettings.showPreviewOnLaunch,
                )
                putExtra(
                    EXTRA_OVERLAY_OPACITY,
                    settings.photoCaptureSettings.overlayOpacity,
                )
                putExtra(
                    EXTRA_INITIAL_ZOOM,
                    if (settings.photoCaptureSettings.rememberZoom) {
                        settings.photoCaptureSettings.lastZoomRatio
                    } else {
                        1f
                    },
                )
                putExtra(
                    EXTRA_REMEMBER_ZOOM,
                    settings.photoCaptureSettings.rememberZoom,
                )
                putExtra(EXTRA_START_IN_BACKGROUND, startInBackground)
                settings.saveFolder?.let {
                    putExtra(EXTRA_SAVE_FOLDER, it)
                }
            }
        }
    }
}
