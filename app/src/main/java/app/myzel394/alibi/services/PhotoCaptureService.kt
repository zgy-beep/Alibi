package app.myzel394.alibi.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
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
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import app.myzel394.alibi.db.PhotoCaptureExtensionMode
import app.myzel394.alibi.helpers.resolvePhotoCaptureExtension
import app.myzel394.alibi.helpers.shouldUseSoftwareJpegEncoder
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
import app.myzel394.alibi.helpers.photoTargetSize
import app.myzel394.alibi.helpers.usesQualityPriority
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        val useQualityPriority: Boolean,
        val frameRateRange: Range<Int>?,
        val useOpticalStabilization: Boolean,
        val useContinuousAutoFocus: Boolean,
        val useHighQualityNoiseReduction: Boolean,
        val useHighQualityEdge: Boolean,
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
    private val smoothZoom = SmoothZoom()
    private val zoomRequests = ZoomRequestQueue()
    private val overlayZoomThrottle = ZoomUiUpdateThrottle()
    private var zoomTickerJob: Job? = null
    private var zoomTickerLastFrameNanos = 0L
    private var lastZoomInputAtMillis = 0L
    private var lastSubmittedZoomRatio = Float.NaN
    private var lastRememberedZoomRatio = Float.NaN
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

    /**
     * Relative pinch input. The accumulated target is eased toward by a ~16 ms
     * ticker, so the preview tracks the fingers continuously instead of
     * stepping once per touch sample, and at most one CameraX request is in
     * flight at a time (see [ZoomRequestQueue]).
     */
    fun zoomBy(scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f || scaleFactor == 1f) {
            return
        }
        if (!state.isActive || isStopping) {
            return
        }
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        smoothZoom.setBounds(zoomState.minZoomRatio, zoomState.maxZoomRatio)

        val previousTarget = smoothZoom.targetRatio
        val target = smoothZoom.scaleBy(scaleFactor)
        // The gesture pushed against the supported min/max ratio: nothing new
        // to animate.
        if (abs(target - previousTarget) < ZOOM_TARGET_EPSILON) {
            return
        }

        lastZoomInputAtMillis = SystemClock.elapsedRealtime()
        zoomInputCount++
        // Persisting is debounced internally; the ticker's per-frame frames only
        // re-arm the debounce when the *target* moved materially.
        maybeRememberFloatingZoom(target, force = false)
        startZoomTicker()
    }

    /**
     * Absolute zoom input (floating overlay slider/step buttons, restored
     * session zoom). Absolute inputs snap and are submitted immediately:
     * easing a deliberate input reads as sluggishness.
     */
    fun setZoomRatio(target: Float) {
        if (!target.isFinite()) {
            return
        }
        if (!state.isActive || isStopping) {
            return
        }
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        smoothZoom.setBounds(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        val clamped = smoothZoom.snapTo(target)
        cancelZoomTicker()

        if (abs(clamped - state.zoomRatio) >= ZOOM_EPSILON) {
            // Zoom-only update: update the session state optimistically without
            // publishing it, so the capture screen does not recompose.
            state = state.copy(zoomRatio = clamped)
            lastZoomInputAtMillis = SystemClock.elapsedRealtime()
            zoomInputCount++
            onZoomChanged?.invoke(clamped)
            maybeRememberFloatingZoom(clamped, force = true)
        }
        // Even a no-op absolute input flushes the overlay: it may still be
        // showing an eased ratio from an interrupted pinch.
        updateOverlayZoom(ratio = clamped, force = true)
        submitZoomRequest(clamped)
    }

    /**
     * Drives the eased zoom at display cadence. Runs only while a pinch is
     * active/settling and cancels itself as soon as the target is reached, the
     * session stops, or the camera goes away.
     */
    private fun startZoomTicker() {
        if (zoomTickerJob?.isActive == true) {
            return
        }
        zoomTickerLastFrameNanos = SystemClock.elapsedRealtimeNanos()
        zoomTickerJob = scope.launch {
            while (true) {
                delay(ZOOM_TICK_MILLIS)
                if (!state.isActive || isStopping || camera == null) {
                    break
                }
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val elapsedMillis = ((nowNanos - zoomTickerLastFrameNanos) / 1_000_000f)
                    .coerceIn(ZOOM_TICK_MIN_STEP_MILLIS, ZOOM_TICK_MAX_STEP_MILLIS)
                zoomTickerLastFrameNanos = nowNanos

                val ratio = smoothZoom.advance(elapsedMillis)
                val settled = smoothZoom.isSettled
                publishZoomFrame(ratio, force = settled)
                submitZoomRequest(ratio)
                if (settled) {
                    // Make sure the final gesture target is persisted even when
                    // the eased value stopped just short of it.
                    maybeRememberFloatingZoom(smoothZoom.targetRatio, force = true)
                    break
                }
            }
            zoomTickerJob = null
        }
    }

    private fun cancelZoomTicker() {
        zoomTickerJob?.cancel()
        zoomTickerJob = null
    }

    /**
     * Reports an eased zoom frame. Deliberately does not publish the session
     * state: zoom-only changes must not recompose the whole capture screen.
     * Sub-perceptual changes are skipped so a pinch cannot invalidate Compose
     * readers on every display frame; the settled value is always published.
     */
    private fun publishZoomFrame(ratio: Float, force: Boolean) {
        if (!ratio.isFinite()) {
            return
        }
        val changed = abs(ratio - state.zoomRatio) >= ZOOM_STATE_PUBLISH_EPSILON
        if (changed) {
            state = state.copy(zoomRatio = ratio)
            onZoomChanged?.invoke(ratio)
        }
        if (changed || force) {
            updateOverlayZoom(ratio = ratio, force = force)
        }
    }

    /**
     * Submits one ratio to the latest-wins queue. Duplicate consecutive ratios
     * are dropped so settling does not issue redundant CameraX requests.
     */
    private fun submitZoomRequest(ratio: Float) {
        if (!ratio.isFinite()) {
            return
        }
        if (camera?.cameraControl == null) {
            zoomRequests.reset()
            lastSubmittedZoomRatio = Float.NaN
            return
        }
        if (abs(ratio - lastSubmittedZoomRatio) < ZOOM_SUBMIT_EPSILON) {
            return
        }
        lastSubmittedZoomRatio = ratio
        val immediate = zoomRequests.submit(ratio)
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
            lastSubmittedZoomRatio = Float.NaN
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

    /**
     * Pushes the zoom ratio into the floating overlay. A pinch can produce a
     * frame per display refresh; the overlay views are only re-rendered on
     * perceptible changes and at most roughly every 50 ms, with a guaranteed
     * render for the settled value.
     */
    private fun updateOverlayZoom(ratio: Float = state.zoomRatio, force: Boolean = false) {
        val currentSession = session ?: return
        if (currentSession.mode != PhotoCaptureMode.FLOATING || appVisible || isStopping) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!overlayZoomThrottle.shouldRender(ratio, now, force)) {
            return
        }
        overlayZoomThrottle.markRendered(ratio, now)
        val zoomState = camera?.cameraInfo?.zoomState?.value
        overlay.updateZoom(
            ratio = ratio,
            minRatio = zoomState?.minZoomRatio ?: 1f,
            maxRatio = zoomState?.maxZoomRatio ?: 1f,
        )
    }

    /**
     * Floating windows have no black screen UI, so they persist their zoom
     * themselves. The DataStore write stays debounced: eased ticks only re-arm
     * the debounce when the target moved materially, and the final target is
     * always persisted when the zoom settles.
     */
    private fun maybeRememberFloatingZoom(ratio: Float, force: Boolean) {
        val currentSession = session ?: return
        if (currentSession.mode != PhotoCaptureMode.FLOATING ||
            !currentSession.photoSettings.rememberZoom ||
            !ratio.isFinite()
        ) {
            return
        }
        val previous = lastRememberedZoomRatio
        if (!force && previous.isFinite() &&
            abs(ratio - previous) < ZOOM_REMEMBER_MIN_DELTA
        ) {
            return
        }
        lastRememberedZoomRatio = ratio
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
        cancelZoomTicker()
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

    @SuppressLint("RestrictedApi")
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
        lastSubmittedZoomRatio = Float.NaN
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
                extensionMode = runCatching {
                    PhotoCaptureExtensionMode.valueOf(
                        intent.getStringExtra(EXTRA_EXTENSION_MODE)
                            ?: PhotoCaptureExtensionMode.OFF.name,
                    )
                }.getOrDefault(PhotoCaptureExtensionMode.OFF),
            ),
        )
        initializationJob?.cancel()
        stopTimeoutJob?.cancel()
        saveWorkerJob?.cancel()
        cancelZoomTicker()
        smoothZoom.reset(1f)
        lastRememberedZoomRatio = Float.NaN
        overlayZoomThrottle.reset()
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
                val extensionSelection = resolveCameraExtension(
                    provider = provider,
                    selector = selector,
                    requested = sessionConfig.photoSettings.extensionMode,
                )
                val bindSelector = extensionSelection?.cameraSelector ?: selector
                val extensionActive = extensionSelection != null
                val maxHardwareJpegPixels = queryMaxHardwareJpegPixels(cameraInfo)
                val fastCaptureProfile = buildFastCaptureProfile(
                    cameraInfo = cameraInfo,
                    settings = sessionConfig.photoSettings,
                    extensionActive = extensionActive,
                )
                val useSoftwareJpegEncoder = !extensionActive && shouldUseSoftwareJpegEncoder(
                    qualityPriority = fastCaptureProfile.useQualityPriority,
                    maxHardwareJpegPixels = maxHardwareJpegPixels,
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
                val captureMode = captureModeFor(fastCaptureProfile)
                val jpegQuality = jpegQualityFor(fastCaptureProfile)
                if (extensionActive && sessionConfig.photoSettings.flashMode != PhotoFlashMode.OFF) {
                    Log.i(TAG, "Camera extension active; flash is disabled")
                }
                Log.i(
                    TAG,
                    "Image capture mode=$captureMode jpegQuality=$jpegQuality " +
                        "resolution=${sessionConfig.photoSettings.resolutionPreference} " +
                        "aspect=${sessionConfig.photoSettings.aspectRatio} " +
                        "qualityPriority=${fastCaptureProfile.useQualityPriority} " +
                        "hqNoiseReduction=${fastCaptureProfile.useHighQualityNoiseReduction} " +
                        "hqEdge=${fastCaptureProfile.useHighQualityEdge} " +
                        "softwareJpeg=$useSoftwareJpegEncoder " +
                        "hardwareJpegMaxPixels=${maxHardwareJpegPixels ?: "unknown"} " +
                        "extension=${extensionSelection?.let { extensionLabel(it.mode) } ?: "NONE"}",
                )
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(captureMode)
                    .setJpegQuality(jpegQuality)
                    .setSoftwareJpegEncoderRequested(useSoftwareJpegEncoder)
                    .setFlashMode(
                        if (extensionActive) {
                            ImageCapture.FLASH_MODE_OFF
                        } else {
                            when (sessionConfig.photoSettings.flashMode) {
                                PhotoFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                                PhotoFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                PhotoFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                            }
                        }
                    )
                    .setResolutionSelector(
                        captureResolutionSelector(sessionConfig.photoSettings)
                    )
                    .setTargetRotation(currentDisplayRotation())
                if (!extensionActive) {
                    applyFastCaptureOptions(
                        Camera2Interop.Extender<ImageCapture>(captureBuilder),
                        fastCaptureProfile,
                    )
                }
                val capture = captureBuilder.build()

                val previewUseCase = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector(sessionConfig.photoSettings))
                    .setTargetRotation(currentDisplayRotation())
                    .build()
                // Quality stills use preview + capture only. The extra analysis
                // surface combined with a non-ZSL high-resolution JPEG pipeline
                // makes CameraX 1.3.4 fail to open the capture session on the
                // API 36 emulator; preview keeps AE/AF running.
                val analysis = if (
                    fastCaptureProfile.useQualityPriority ||
                    (extensionSelection != null && !extensionSelection.analysisSupported)
                ) {
                    null
                } else {
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
                    if (!extensionActive) {
                        applyFastCaptureOptions(
                            Camera2Interop.Extender<ImageAnalysis>(analysisBuilder),
                            fastCaptureProfile,
                        )
                    }
                    analysisBuilder
                        .build()
                        .also { useCase ->
                            useCase.setAnalyzer(cameraExecutor) { image ->
                                image.close()
                            }
                        }
                }
                if (!isCurrentSession(sessionConfig.id) || isStopping) {
                    analysis?.clearAnalyzer()
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
                        bindSelector,
                        *listOfNotNull(previewUseCase, capture, analysis).toTypedArray(),
                    )
                } catch (error: IllegalArgumentException) {
                    Log.w(
                        TAG,
                        "Fast photo use-case combination is unavailable; retrying without analysis",
                        error,
                    )
                    if (extensionActive) {
                        Log.w(TAG, "Camera extension binding failed; using standard camera")
                    }
                    analysis?.clearAnalyzer()
                    imageAnalysis = null
                    val fallbackBuilder = ImageCapture.Builder()
                        .setCaptureMode(captureMode)
                        .setJpegQuality(jpegQuality)
                        .setSoftwareJpegEncoderRequested(
                            shouldUseSoftwareJpegEncoder(
                                qualityPriority = fastCaptureProfile.useQualityPriority,
                                maxHardwareJpegPixels = maxHardwareJpegPixels,
                            )
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
                        Camera2Interop.Extender<ImageCapture>(fallbackBuilder),
                        fastCaptureProfile,
                    )
                    val fallbackCapture = fallbackBuilder.build()
                    imageCapture = fallbackCapture
                    val fallbackBind = runCatching {
                        provider.bindToLifecycle(
                            this@PhotoCaptureService,
                            selector,
                            previewUseCase,
                            fallbackCapture,
                        )
                    }
                    fallbackBind.getOrElse { fallbackError ->
                        // Last resort: the 1080-class preview selector itself may
                        // be unsupported by this camera/extension combination.
                        // Rebind with CameraX's default preview resolution so the
                        // session still starts (soft viewfinder instead of no
                        // viewfinder).
                        Log.w(
                            TAG,
                            "Preview resolution selector was rejected; " +
                                "retrying with the default preview resolution",
                            fallbackError,
                        )
                        previewUseCase.setSurfaceProvider(null)
                        val defaultPreview = Preview.Builder()
                            .setTargetRotation(currentDisplayRotation())
                            .build()
                        preview = defaultPreview
                        defaultPreview.setSurfaceProvider(previewSurfaceProvider)
                        provider.bindToLifecycle(
                            this@PhotoCaptureService,
                            selector,
                            defaultPreview,
                            fallbackCapture,
                        )
                    }
                }
                if (!isCurrentSession(sessionConfig.id) || isStopping) {
                    releaseCamera()
                    return@launch
                }
                val zoomState = camera?.cameraInfo?.zoomState?.value
                smoothZoom.setBounds(
                    zoomState?.minZoomRatio ?: 1f,
                    zoomState?.maxZoomRatio ?: 1f,
                )
                val initialZoom = smoothZoom.snapTo(sessionConfig.photoSettings.lastZoomRatio)
                if (initialZoom != 1f) {
                    camera?.cameraControl?.setZoomRatio(initialZoom)
                }
                // The restore was applied directly, so a later identical request
                // does not need to go through the queue again.
                lastSubmittedZoomRatio = initialZoom
                overlayZoomThrottle.reset()
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
        extensionActive: Boolean = false,
    ): FastCaptureProfile {
        val strategy = settings.getEffectiveCaptureStrategy()
        val useQualityPriority = usesQualityPriority(settings.resolutionPreference)
        // "Quality" and vendor extensions trade ZSL and forced fast frame rates
        // for larger frames and higher-quality still processing; other profiles
        // keep the fast path.
        val useFastOptions = strategy != PhotoCaptureStrategy.BALANCED &&
            !useQualityPriority &&
            !extensionActive
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
        val highQualityNoiseReductionSupported = useQualityPriority && runCatching {
            Camera2CameraInfo
                .from(cameraInfo)
                .getCameraCharacteristic(
                    CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
                )
                ?.contains(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true
        }.getOrDefault(false)
        val highQualityEdgeSupported = useQualityPriority && runCatching {
            Camera2CameraInfo
                .from(cameraInfo)
                .getCameraCharacteristic(
                    CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES
                )
                ?.contains(CameraMetadata.EDGE_MODE_HIGH_QUALITY) == true
        }.getOrDefault(false)

        return FastCaptureProfile(
            useZeroShutterLag = zeroShutterLagSupported,
            useQualityPriority = useQualityPriority,
            frameRateRange = fastFrameRateRange,
            useOpticalStabilization = settings.opticalStabilizationEnabled &&
                opticalStabilizationSupported,
            useContinuousAutoFocus = settings.continuousAutoFocusEnabled &&
                continuousAutoFocusSupported,
            useHighQualityNoiseReduction = highQualityNoiseReductionSupported,
            useHighQualityEdge = highQualityEdgeSupported,
        )
    }

    private fun queryMaxHardwareJpegPixels(cameraInfo: CameraInfo): Long? {
        return runCatching {
            Camera2CameraInfo
                .from(cameraInfo)
                .getCameraCharacteristic(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.maxOfOrNull { it.width.toLong() * it.height.toLong() }
        }.getOrNull()
    }

    private data class CameraExtensionSelection(
        val mode: Int,
        val cameraSelector: CameraSelector,
        val analysisSupported: Boolean,
    )

    private fun extensionLabel(mode: Int): String {
        return when (mode) {
            ExtensionMode.NIGHT -> "NIGHT"
            ExtensionMode.HDR -> "HDR"
            else -> "NONE"
        }
    }

    private suspend fun resolveCameraExtension(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        requested: PhotoCaptureExtensionMode,
    ): CameraExtensionSelection? {
        if (requested == PhotoCaptureExtensionMode.OFF) {
            return null
        }
        val manager = try {
            withTimeoutOrNull(EXTENSION_INIT_TIMEOUT_MILLIS) {
                ExtensionsManager.getInstanceAsync(this@PhotoCaptureService, provider).awaitCameraFuture()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not initialize CameraX extensions; using standard camera", error)
            null
        }
        if (manager == null) {
            Log.i(TAG, "CameraX extensions unavailable or timed out; using standard camera")
            return null
        }

        val nightAvailable = runCatching {
            manager.isExtensionAvailable(selector, ExtensionMode.NIGHT)
        }.getOrDefault(false)
        val hdrAvailable = runCatching {
            manager.isExtensionAvailable(selector, ExtensionMode.HDR)
        }.getOrDefault(false)
        val selected = resolvePhotoCaptureExtension(requested, nightAvailable, hdrAvailable)
        Log.i(
            TAG,
            "Camera extensions requested=$requested nightAvailable=$nightAvailable " +
                "hdrAvailable=$hdrAvailable selected=$selected",
        )
        if (selected == PhotoCaptureExtensionMode.OFF) {
            return null
        }
        val mode = when (selected) {
            PhotoCaptureExtensionMode.NIGHT -> ExtensionMode.NIGHT
            PhotoCaptureExtensionMode.HDR -> ExtensionMode.HDR
            else -> return null
        }
        val extensionSelector = runCatching {
            manager.getExtensionEnabledCameraSelector(selector, mode)
        }.onFailure {
            Log.w(TAG, "Could not enable CameraX extension ${extensionLabel(mode)}", it)
        }.getOrNull() ?: return null
        val analysisSupported = runCatching {
            manager.isImageAnalysisSupported(selector, mode)
        }.getOrDefault(false)
        return CameraExtensionSelection(
            mode = mode,
            cameraSelector = extensionSelector,
            analysisSupported = analysisSupported,
        )
    }

    private suspend fun <T> ListenableFuture<T>.awaitCameraFuture(): T {
        val service = this@PhotoCaptureService
        return suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (error: ExecutionException) {
                        continuation.resumeWithException(error.cause ?: error)
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                },
                ContextCompat.getMainExecutor(service),
            )
            continuation.invokeOnCancellation { runCatching { cancel(true) } }
        }
    }

    private fun captureModeFor(profile: FastCaptureProfile): Int {
        // On the API 36 emulator CameraX 1.5.3's hardware JPEG path produced
        // quality-95 quantisation tables even with JPEG 100 / MAXIMIZE_QUALITY
        // requested. The quality profile therefore requests a software JPEG
        // encoder (which honours quality 100 deterministically) while keeping
        // the low-latency capture mode; the fast/ZSL profile is unchanged.
        return if (profile.useZeroShutterLag) {
            ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
        } else {
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    private fun jpegQualityFor(profile: FastCaptureProfile): Int {
        return when {
            profile.useQualityPriority -> 100
            profile.useZeroShutterLag -> 95
            else -> 98
        }
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
        if (profile.useHighQualityNoiseReduction) {
            builder.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY,
            )
        }
        if (profile.useHighQualityEdge) {
            builder.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CameraMetadata.EDGE_MODE_HIGH_QUALITY,
            )
        }
    }

    private fun captureResolutionSelector(settings: PhotoCaptureSettings): ResolutionSelector {
        val aspectRatio = when (settings.aspectRatio) {
            PhotoAspectRatio.FOUR_THREE -> AspectRatio.RATIO_4_3
            PhotoAspectRatio.SIXTEEN_NINE -> AspectRatio.RATIO_16_9
        }
        val targetSize = photoTargetSize(
            settings.resolutionPreference,
            settings.aspectRatio,
        )
        val target = Size(targetSize.width, targetSize.height)
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

    /**
     * The preview used to be built without a resolution selector, so CameraX
     * picked its default 640x480 frame and the revealed viewfinder looked soft
     * next to the captured photo. Request a 1080-class frame that matches the
     * configured capture aspect ratio instead; the fallback rule keeps the bind
     * working on cameras that do not expose the exact target size.
     */
    private fun previewResolutionSelector(settings: PhotoCaptureSettings): ResolutionSelector {
        val aspectRatio = when (settings.aspectRatio) {
            PhotoAspectRatio.FOUR_THREE -> AspectRatio.RATIO_4_3
            PhotoAspectRatio.SIXTEEN_NINE -> AspectRatio.RATIO_16_9
        }
        val target = when (settings.aspectRatio) {
            PhotoAspectRatio.FOUR_THREE -> PREVIEW_TARGET_4_3
            PhotoAspectRatio.SIXTEEN_NINE -> PREVIEW_TARGET_16_9
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
        cancelZoomTicker()
        zoomRequests.reset()
        smoothZoom.reset(1f)
        lastSubmittedZoomRatio = Float.NaN
        lastRememberedZoomRatio = Float.NaN
        overlayZoomThrottle.reset()
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
            updateOverlayZoom(force = true)
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
        const val EXTRA_EXTENSION_MODE = "extensionMode"
        const val EXTRA_START_IN_BACKGROUND = "startInBackground"
        private const val REQUEST_STOP = 41
        private const val REQUEST_OPEN = 42
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val RECOVERY_REQUEST_ID = -1L
        private const val MIN_FAST_CAPTURE_FPS = 30
        private const val MOTION_CAPTURE_FPS = 60
        private const val ZOOM_EPSILON = 0.005f
        /** Target change below this counts as "no new gesture sample" (clamped). */
        private const val ZOOM_TARGET_EPSILON = 0.0005f
        /** A zoom frame smaller than this is sub-perceptual and not published. */
        private const val ZOOM_STATE_PUBLISH_EPSILON = 0.001f
        /** Ratios closer than this are the same request; do not resubmit. */
        private const val ZOOM_SUBMIT_EPSILON = 0.000001f
        /** Material target change that re-arms the floating zoom persistence. */
        private const val ZOOM_REMEMBER_MIN_DELTA = 0.01f
        private const val ZOOM_TICK_MILLIS = 16L
        private const val ZOOM_TICK_MIN_STEP_MILLIS = 1f
        private const val ZOOM_TICK_MAX_STEP_MILLIS = 64f
        private const val ZOOM_REMEMBER_DELAY_MILLIS = 400L
        private val PREVIEW_TARGET_4_3 = Size(1440, 1080)
        private val PREVIEW_TARGET_16_9 = Size(1920, 1080)
        private const val EXTENSION_INIT_TIMEOUT_MILLIS = 3_000L

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
                putExtra(
                    EXTRA_EXTENSION_MODE,
                    settings.photoCaptureSettings.extensionMode.name,
                )
                putExtra(EXTRA_START_IN_BACKGROUND, startInBackground)
                settings.saveFolder?.let {
                    putExtra(EXTRA_SAVE_FOLDER, it)
                }
            }
        }
    }
}

/**
 * Pure decision logic for throttling zoom UI repaints.
 *
 * A pinch can produce an eased zoom frame per display refresh, but the floating
 * overlay's text/seek-bar only need to be re-rendered when the ratio moved by a
 * perceptible amount, and never more often than every [minIntervalMillis].
 * Callers force a render for the settled value so the overlay always ends on
 * the exact final ratio. Free of Android types so it is unit tested on the JVM.
 */
internal class ZoomUiUpdateThrottle(
    private val minRelativeDelta: Float = DEFAULT_MIN_RELATIVE_DELTA,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private var lastRenderedRatio = Float.NaN
    private var lastRenderedAtMillis = Long.MIN_VALUE

    fun shouldRender(ratio: Float, nowMillis: Long, force: Boolean): Boolean {
        if (force) {
            return true
        }
        if (!ratio.isFinite()) {
            return false
        }
        if (!lastRenderedRatio.isFinite()) {
            return true
        }
        val reference = if (lastRenderedRatio > 0f) lastRenderedRatio else 1f
        if (abs(ratio - lastRenderedRatio) < minRelativeDelta * reference) {
            return false
        }
        return nowMillis >= lastRenderedAtMillis + minIntervalMillis
    }

    fun markRendered(ratio: Float, nowMillis: Long) {
        if (ratio.isFinite()) {
            lastRenderedRatio = ratio
        }
        lastRenderedAtMillis = nowMillis
    }

    fun reset() {
        lastRenderedRatio = Float.NaN
        lastRenderedAtMillis = Long.MIN_VALUE
    }

    companion object {
        /** ~0.5 % of the current ratio is a visible step in the overlay label. */
        const val DEFAULT_MIN_RELATIVE_DELTA = 0.005f
        const val DEFAULT_MIN_INTERVAL_MILLIS = 50L
    }
}
