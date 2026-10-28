package app.myzel394.alibi.services

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.myzel394.alibi.NotificationHelper
import app.myzel394.alibi.db.RecordingInformation
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.helpers.BatchesFolder
import app.myzel394.alibi.helpers.VideoBatchesFolder
import app.myzel394.alibi.ui.SUPPORTS_SAVING_VIDEOS_IN_CUSTOM_FOLDERS
import app.myzel394.alibi.ui.SUPPORTS_SCOPED_STORAGE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.properties.Delegates

class VideoRecorderService :
    IntervalRecorderService<RecordingInformation, VideoBatchesFolder>() {
    override var batchesFolder = VideoBatchesFolder.viaInternalFolder(this)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    // The standard camera screen renders the live viewfinder through this
    // service-owned use case; covert mode simply never attaches a surface.
    private var preview: Preview? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    /**
     * Supplied by the standard camera screen from
     * `PreviewView.getMeteringPointFactory()`; it already applies the
     * viewfinder's FILL_CENTER transform, so [focusAt] receives raw view pixels.
     */
    private var meteringPointFactory: MeteringPointFactory? = null
    /** Invalidates focus results that arrive after the camera was closed. */
    private var focusRequestGeneration = 0

    // Used to listen and check if the camera is available
    private val _cameraAvailableListener = CompletableDeferred<Unit>()
    private var _videoFinalizerListener: CompletableDeferred<Unit>? = null
    private var cameraReady = false
    private var pendingCycleStart = false

    // Absolute last completer that can be awaited to ensure that the camera is closed
    private var _cameraCloserListener: CompletableDeferred<Unit>? = null

    private lateinit var selectedCamera: CameraSelector
    private var enableAudio by Delegates.notNull<Boolean>()

    /** Zoom ratio requested by the preparation sheet; applied as soon as the camera is bound. */
    private var initialZoom = 1f

    var onCameraControlAvailable = {}

    /** Reports the live (eased) zoom ratio and the camera's supported range. */
    var onZoomChanged: ((ratio: Float, minRatio: Float, maxRatio: Float) -> Unit)? = null

    /** Reports the outcome of the last [focusAt] request on the main thread. */
    var onFocusChanged: ((focused: Boolean) -> Unit)? = null

    var cameraControl: CameraControl? = null
        private set

    /** Zoom ratio currently applied to the camera, readable for device acceptance checks. */
    val cameraZoomRatio: Float
        get() = cameraControl?.zoomRatio ?: initialZoom

    /** Changes the zoom ratio of the bound camera; ignored until the camera is ready. */
    fun setZoomRatio(ratio: Float) {
        cameraControl?.setZoomRatio(ratio)
    }

    /** Multiplies the current zoom ratio, e.g. for step buttons. */
    fun zoomBy(factor: Float) {
        cameraControl?.zoomBy(factor)
    }

    /**
     * Renders the recorder's own preview into [provider] while a recording is
     * running. Clearing it only detaches the viewfinder; the recording keeps
     * running. The provider is remembered, so a call that arrives before the
     * camera finished opening is applied as soon as the preview use case exists.
     */
    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = provider
        runCatching { preview?.setSurfaceProvider(provider) }
    }

    /**
     * Coordinate mapper for [focusAt]. The standard camera screen passes the
     * `PreviewView.getMeteringPointFactory()` of its viewfinder; pass `null`
     * when the viewfinder is detached.
     */
    fun setMeteringPointFactory(factory: MeteringPointFactory?) {
        meteringPointFactory = factory
    }

    /**
     * Starts an AF+AE focus/metering pass at [xPixels]/[yPixels] (viewfinder
     * pixel space) and reports the outcome through [onFocusChanged] on the main
     * thread. A missing camera/factory or a CameraX failure reports `false`, so
     * the UI can always settle its focus indicator.
     */
    fun focusAt(xPixels: Float, yPixels: Float) {
        val boundCamera = camera
        val factory = meteringPointFactory
        if (!cameraReady || boundCamera == null || factory == null ||
            !xPixels.isFinite() || !yPixels.isFinite()
        ) {
            onFocusChanged?.invoke(false)
            return
        }

        val point = runCatching { factory.createPoint(xPixels, yPixels) }.getOrNull()
        if (point == null) {
            onFocusChanged?.invoke(false)
            return
        }
        val generation = ++focusRequestGeneration
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()
        val future = runCatching {
            boundCamera.cameraControl.startFocusAndMetering(action)
        }.getOrNull()
        if (future == null) {
            onFocusChanged?.invoke(false)
            return
        }
        future.addListener(
            {
                // A newer focus request or a camera teardown makes this result
                // stale: never report it to the UI.
                if (generation != focusRequestGeneration) {
                    return@addListener
                }
                val focused = runCatching { future.get().isFocusSuccessful }
                    .getOrDefault(false)
                onFocusChanged?.invoke(focused)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    /** Display rotation the preview buffers are transformed with. */
    private fun currentDisplayRotation(): Int {
        val displayManager = getSystemService(DisplayManager::class.java)
        return displayManager
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
            ?: Surface.ROTATION_0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "init") {
            selectedCamera = CameraSelector.Builder().requireLensFacing(
                intent.getIntExtra("cameraID", CameraSelector.LENS_FACING_BACK)
            ).build()
            enableAudio = intent.getBooleanExtra("enableAudio", true)
            initialZoom = intent.getFloatExtra("initialZoom", 1f)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun start() {
        super.start()

        scope.launch {
            openCamera()
        }
    }

    override suspend fun awaitReady() {
        withTimeout(CAMERA_CLOSE_TIMEOUT) {
            _cameraAvailableListener.await()
        }
        if (state == RecorderState.ERROR) {
            throw IllegalStateException("Camera failed to become ready")
        }
    }

    override suspend fun stop() {
        super.stop()

        stopActiveRecording()

        // Camera can only be closed after the recording has been finalized
        _videoFinalizerListener?.let { finalizer ->
            withTimeoutOrNull(CAMERA_CLOSE_TIMEOUT) { finalizer.await() }
        }

        closeCamera()

        _cameraCloserListener?.let { closer ->
            withTimeoutOrNull(CAMERA_CLOSE_TIMEOUT) { closer.await() }
        }
    }

    override fun pause() {
        super.pause()

        stopActiveRecording()
    }

    override fun startForegroundService() {
        ServiceCompat.startForeground(
            this,
            NotificationHelper.RECORDER_CHANNEL_NOTIFICATION_ID,
            getNotificationHelper().buildStartingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (enableAudio)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                else
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            },
        )
    }

    @SuppressLint("MissingPermission")
    override fun startNewCycle() {
        if (!cameraReady) {
            pendingCycleStart = true
            return
        }

        super.startNewCycle()

        fun action() {
            try {
                stopActiveRecording()
                val newRecording = prepareVideoRecording()
                val finalizer = CompletableDeferred<Unit>()
                _videoFinalizerListener = finalizer

                activeRecording = newRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize &&
                        (
                            this@VideoRecorderService.state == RecorderState.STOPPED ||
                                this@VideoRecorderService.state == RecorderState.PAUSED
                            )
                    ) {
                        finalizer.complete(Unit)
                    }
                }
            } catch (error: Exception) {
                failCycle(error)
            }
        }

        action()
    }


    // Runs a function in the main thread
    private fun runOnMain(callback: () -> Unit) {
        val mainHandler = ContextCompat.getMainExecutor(this)

        mainHandler.execute(callback)
    }

    private fun buildRecorder() = Recorder.Builder()
        .setQualitySelector(
            settings.videoRecorderSettings.getQualitySelector()
                ?: QualitySelector.from(Quality.HIGHEST)
        )
        .apply {
            if (settings.videoRecorderSettings.targetedVideoBitRate != null) {
                setTargetVideoEncodingBitRate(settings.videoRecorderSettings.targetedVideoBitRate!!)
            }
        }
        .build()

    private fun buildVideoCapture(recorder: Recorder, provider: ProcessCameraProvider) =
        VideoCapture.Builder(recorder)
        .apply {
            val frameRate = settings.videoRecorderSettings.targetFrameRate
            if (frameRate != null) {
                setTargetFrameRate(Range(frameRate, frameRate))
            }
            // Only request stabilization when the selected camera advertises
            // it; requesting it unconditionally fails the binding on devices
            // that do not support it.
            if (settings.videoRecorderSettings.videoStabilizationEnabled &&
                provider.supportsVideoStabilization(selectedCamera)
            ) {
                setVideoStabilizationEnabled(true)
            }
        }
        .build()

    /**
     * Viewfinder use case used by the standard camera screen while recording.
     * Recording video is usually 16:9, so request a 720p-class 16:9 buffer
     * instead of CameraX's soft 640x480 default (see AGENTS.md); the fallback
     * rule keeps the bind working on cameras without the exact size.
     */
    private fun buildPreviewUseCase(): Preview = Preview.Builder()
        .setResolutionSelector(VIDEO_PREVIEW_RESOLUTION_SELECTOR)
        .setTargetRotation(currentDisplayRotation())
        .build()

    /**
     * CameraX's stabilization request only works when the selected camera lists
     * the ON mode in its Camera2 characteristics. Any lookup failure (missing
     * characteristic, unsupported provider) simply disables the feature instead
     * of crashing the recording service.
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun ProcessCameraProvider.supportsVideoStabilization(
        selector: CameraSelector,
    ): Boolean = runCatching {
        val cameraInfo = selector.filter(availableCameraInfos).firstOrNull()
            ?: return@runCatching false
        val availableModes = Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )
        availableModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
    }.getOrDefault(false)

    // Open the camera.
    // Used to open it for a longer time, shouldn't be called when pausing / resuming.
    // This should only be called when starting a recording.
    private suspend fun openCamera() {
        try {
            val provider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(this@VideoRecorderService).get()
            }
            currentCoroutineContext().ensureActive()
            if (state == RecorderState.STOPPED) {
                return
            }
            cameraProvider = provider
            val recorder = buildRecorder()
            val capture = buildVideoCapture(recorder, provider)
            videoCapture = capture

            runOnMain {
                if (state == RecorderState.STOPPED || cameraProvider == null) {
                    return@runOnMain
                }
                try {
                    _cameraCloserListener = CompletableDeferred()

                    // The recorder owns a Preview use case together with the
                    // VideoCapture so the standard camera screen can show a live
                    // viewfinder while recording. Covert mode never attaches a
                    // surface, which CameraX accepts.
                    val provider = cameraProvider!!
                    var previewUseCase = buildPreviewUseCase()
                    preview = previewUseCase
                    // The screen may have handed its surface provider over before
                    // the camera finished opening; apply it right away.
                    previewUseCase.setSurfaceProvider(previewSurfaceProvider)
                    camera = try {
                        provider.bindToLifecycle(
                            this@VideoRecorderService,
                            selectedCamera,
                            capture,
                            previewUseCase,
                        )
                    } catch (error: IllegalArgumentException) {
                        // The requested 720p-class preview size can be rejected by
                        // camera/use-case combinations that cannot run it next to
                        // the video capture. Fall back to CameraX's default
                        // preview resolution so the viewfinder still appears.
                        Log.w(
                            TAG,
                            "Preview resolution selector was rejected; " +
                                "retrying with the default preview resolution",
                            error,
                        )
                        previewUseCase.setSurfaceProvider(null)
                        runCatching { provider.unbind(previewUseCase) }
                        previewUseCase = Preview.Builder()
                            .setTargetRotation(currentDisplayRotation())
                            .build()
                        preview = previewUseCase
                        previewUseCase.setSurfaceProvider(previewSurfaceProvider)
                        try {
                            provider.bindToLifecycle(
                                this@VideoRecorderService,
                                selectedCamera,
                                capture,
                                previewUseCase,
                            )
                        } catch (fallbackError: IllegalArgumentException) {
                            // Last resort: keep recording (covert modes never show
                            // a viewfinder) instead of failing the whole cycle.
                            Log.w(
                                TAG,
                                "Preview cannot be combined with the video capture; " +
                                    "recording without a viewfinder",
                                fallbackError,
                            )
                            previewUseCase.setSurfaceProvider(null)
                            runCatching { provider.unbind(previewUseCase) }
                            preview = null
                            provider.bindToLifecycle(
                                this@VideoRecorderService,
                                selectedCamera,
                                capture,
                            )
                        }
                    }

                    cameraControl = CameraControl(
                        camera = camera!!,
                        mainExecutor = ContextCompat.getMainExecutor(this@VideoRecorderService),
                    ).also {
                        it.onZoomChanged = { ratio, minRatio, maxRatio ->
                            this@VideoRecorderService.onZoomChanged
                                ?.invoke(ratio, minRatio, maxRatio)
                        }
                        it.init()
                        // Applies the ratio picked in the preparation sheet
                        // before the first cycle starts, so the recorded file
                        // starts at the chosen zoom.
                        it.applyInitialZoom(initialZoom)
                    }
                    cameraReady = true
                    onCameraControlAvailable()

                    if (pendingCycleStart) {
                        pendingCycleStart = false
                        startNewCycle()
                    }
                    if (!_cameraAvailableListener.isCompleted &&
                        state != RecorderState.ERROR
                    ) {
                        _cameraAvailableListener.complete(Unit)
                    }
                } catch (error: Exception) {
                    failCycle(error)
                    if (!_cameraAvailableListener.isCompleted) {
                        _cameraAvailableListener.completeExceptionally(error)
                    }
                }
            }
        } catch (error: Exception) {
            failCycle(error)
            if (!_cameraAvailableListener.isCompleted) {
                _cameraAvailableListener.completeExceptionally(error)
            }
        }
    }

    // Close the camera
    // Used to close it finally, shouldn't be called when pausing / resuming.
    // This should only be called after recording has finished.
    private fun closeCamera() {
        runOnMain {
            // A focus result for the camera going away must never reach the UI.
            focusRequestGeneration++
            runCatching { camera?.cameraControl?.cancelFocusAndMetering() }

            val capture = videoCapture
            val previewUseCase = preview
            // Detach the viewfinder first; the recording output is unaffected.
            previewUseCase?.setSurfaceProvider(null)
            runCatching {
                when {
                    capture != null && previewUseCase != null ->
                        cameraProvider?.unbind(capture, previewUseCase)

                    capture != null -> cameraProvider?.unbind(capture)
                    previewUseCase != null -> cameraProvider?.unbind(previewUseCase)
                }
            }
            // Drop any queued/in-flight zoom request; the camera is going away.
            cameraControl?.reset()
            _cameraCloserListener?.complete(Unit)
            _cameraCloserListener = null

            // Doesn't need to run on main thread, but
            // if it runs outside `runOnMain`, `cameraProvider` is already null
            // before it's unbound
            cameraProvider = null
            videoCapture = null
            preview = null
            camera = null
            cameraReady = false
            pendingCycleStart = false
        }
    }

    override fun onDestroy() {
        stopActiveRecording()
        _videoFinalizerListener?.complete(Unit)
        _cameraCloserListener?.complete(Unit)
        // The zoom ticker lives in the camera control; never let it outlive us.
        cameraControl?.reset()
        cameraControl = null
        scope.cancel()
        super.onDestroy()
    }

    // `resume` override not needed as `startNewCycle` is called by `IntervalRecorderService`

    private fun stopActiveRecording() {
        runCatching {
            activeRecording?.stop()
        }
    }

    private fun getNameForMediaFile() =
        "${batchesFolder.mediaPrefix}$counter.${settings.videoRecorderSettings.fileExtension}"

    @SuppressLint("MissingPermission", "NewApi")
    private fun prepareVideoRecording() =
        videoCapture!!.output
            .let {
                if (batchesFolder.type == BatchesFolder.BatchType.CUSTOM && SUPPORTS_SAVING_VIDEOS_IN_CUSTOM_FOLDERS) {
                    it.prepareRecording(
                        this,
                        FileDescriptorOutputOptions.Builder(
                            batchesFolder.asCustomGetParcelFileDescriptor(
                                counter,
                                settings.videoRecorderSettings.fileExtension
                            )
                        ).build()
                    )
                } else if (batchesFolder.type == BatchesFolder.BatchType.MEDIA) {
                    if (SUPPORTS_SCOPED_STORAGE) {
                        val name = getNameForMediaFile()

                        it.prepareRecording(
                            this,
                            MediaStoreOutputOptions
                                .Builder(
                                    contentResolver,
                                    batchesFolder.scopedMediaContentUri,
                                )
                                .setContentValues(
                                    batchesFolder.asMediaGetScopedStorageContentValues(
                                        name
                                    )
                                )
                                .build()
                        )
                    } else {
                        val name = getNameForMediaFile()

                        it.prepareRecording(
                            this,
                            FileOutputOptions
                                .Builder(batchesFolder.asMediaGetLegacyFile(name))
                                .build()
                        )
                    }
                } else {
                    it.prepareRecording(
                        this,
                        FileOutputOptions.Builder(
                            batchesFolder.asInternalGetFile(
                                counter,
                                settings.videoRecorderSettings.fileExtension
                            ).apply {
                                createNewFile()
                            }
                        ).build()
                    )
                }
            }
            .run {
                if (enableAudio) {
                    return@run withAudioEnabled()
                }

                this
            }

    override fun getRecordingInformation() =
        RecordingInformation(
            folderPath = batchesFolder.exportFolderForSettings(),
            recordingStart = recordingStart,
            maxDuration = settings.maxDuration,
            batchesAmount = batchesFolder.getBatchesForFFmpeg().size,
            fileExtension = settings.videoRecorderSettings.fileExtension,
            intervalDuration = settings.intervalDuration,
            type = RecordingInformation.Type.VIDEO,
        )

    companion object {
        private const val TAG = "VideoRecorderService"
        const val CAMERA_CLOSE_TIMEOUT = 20000L

        /** Focus/metering auto-cancel, matching the photo pipeline. */
        private const val FOCUS_AUTO_CANCEL_SECONDS = 3L

        /**
         * 16:9 720p-class viewfinder for the standard camera screen while
         * recording, instead of CameraX's soft 640x480 default (see AGENTS.md).
         */
        private val VIDEO_PREVIEW_RESOLUTION_SELECTOR: ResolutionSelector =
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_16_9,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO,
                    )
                )
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()
    }

    /**
     * Zoom/torch bridge to the bound [Camera].
     *
     * Recording zoom reuses the photo pipeline: relative inputs accumulate on a
     * [SmoothZoom] target and a ~16 ms main-thread ticker eases the camera
     * toward it, submitting at most one `setZoomRatio` at a time through
     * [ZoomRequestQueue]. Absolute inputs snap immediately. Because the zoom is
     * applied at camera level it reaches both the live viewfinder and the
     * recorded file.
     */
    class CameraControl(
        val camera: Camera,
        private val mainExecutor: Executor,
        // Save state for optimistic updates
        var torchEnabled: Boolean = false,
    ) {
        /** Receives the eased ratio plus the camera's supported range. */
        var onZoomChanged: ((ratio: Float, minRatio: Float, maxRatio: Float) -> Unit)? = null

        private val smoothZoom = SmoothZoom()
        private val zoomRequests = ZoomRequestQueue()

        // The ticker only runs while a pinch is settling and dies with the camera.
        private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var tickerJob: Job? = null
        private var tickerLastFrameNanos = 0L
        private var lastSubmittedZoomRatio = Float.NaN
        private var lastPublishedZoomRatio = Float.NaN
        private var closed = false

        /** Ratio that was requested last / is currently applied. */
        var zoomRatio: Float = DEFAULT_ZOOM_RATIO
            private set

        var zoomMinRatio: Float = DEFAULT_ZOOM_RATIO
            private set

        var zoomMaxRatio: Float = DEFAULT_ZOOM_RATIO
            private set

        fun init() {
            torchEnabled = camera.cameraInfo.torchState.value == TorchState.ON
            updateZoomBounds()
        }

        /** Reads the camera's supported zoom range; safe to call at any time. */
        private fun updateZoomBounds() {
            val zoomState = camera.cameraInfo.zoomState.value ?: return
            val min = zoomState.minZoomRatio.takeIf { it.isFinite() && it > 0f }
                ?: DEFAULT_ZOOM_RATIO
            val max = zoomState.maxZoomRatio.takeIf { it.isFinite() && it >= min } ?: min
            zoomMinRatio = min
            zoomMaxRatio = max
            smoothZoom.setBounds(min, max)
        }

        /**
         * Applies the zoom chosen in the preparation sheet, clamped into the
         * range the camera really supports, before the first cycle starts.
         */
        fun applyInitialZoom(ratio: Float) {
            updateZoomBounds()
            cancelTicker()
            val clamped = smoothZoom.snapTo(ratio)
            zoomRatio = clamped
            publishZoom(clamped, force = true)
            submitZoom(clamped)
        }

        /**
         * Absolute zoom input: snaps immediately, because easing a deliberate
         * input (slider, step button) reads as sluggishness.
         */
        fun setZoomRatio(target: Float) {
            if (closed) {
                return
            }
            updateZoomBounds()
            cancelTicker()
            val clamped = smoothZoom.snapTo(target)
            zoomRatio = clamped
            publishZoom(clamped, force = true)
            submitZoom(clamped)
        }

        /**
         * Relative pinch input while recording: accumulates on the gesture
         * target, and the ticker eases the camera toward it on the main thread.
         */
        fun zoomBy(factor: Float) {
            if (closed || !factor.isFinite() || factor <= 0f || factor == 1f) {
                return
            }
            updateZoomBounds()
            val previousTarget = smoothZoom.targetRatio
            val target = smoothZoom.scaleBy(factor)
            // The gesture pushed against the supported min/max ratio.
            if (abs(target - previousTarget) < ZOOM_TARGET_EPSILON) {
                return
            }
            zoomRatio = target
            startTicker()
        }

        /**
         * Drives the eased zoom at display cadence while a pinch is settling.
         * Cancels itself once the gesture target is reached.
         */
        private fun startTicker() {
            if (closed || tickerJob?.isActive == true) {
                return
            }
            tickerLastFrameNanos = SystemClock.elapsedRealtimeNanos()
            tickerJob = tickerScope.launch {
                while (isActive) {
                    delay(ZOOM_TICK_MILLIS)
                    if (closed) {
                        break
                    }
                    val nowNanos = SystemClock.elapsedRealtimeNanos()
                    val elapsedMillis = ((nowNanos - tickerLastFrameNanos) / 1_000_000f)
                        .coerceIn(ZOOM_TICK_MIN_STEP_MILLIS, ZOOM_TICK_MAX_STEP_MILLIS)
                    tickerLastFrameNanos = nowNanos

                    val ratio = smoothZoom.advance(elapsedMillis)
                    val settled = smoothZoom.isSettled
                    zoomRatio = ratio
                    // The settled frame is published unconditionally so the zoom
                    // indicator always ends on the exact gesture target.
                    publishZoom(ratio, force = settled)
                    submitZoom(ratio)
                    if (settled) {
                        break
                    }
                }
                tickerJob = null
            }
        }

        private fun cancelTicker() {
            tickerJob?.cancel()
            tickerJob = null
        }

        /**
         * Reports an eased zoom frame. Sub-perceptual changes are skipped so the
         * ticker cannot invalidate Compose readers on every display frame.
         */
        private fun publishZoom(ratio: Float, force: Boolean) {
            if (!ratio.isFinite()) {
                return
            }
            if (!force && abs(ratio - lastPublishedZoomRatio) < ZOOM_STATE_PUBLISH_EPSILON) {
                return
            }
            lastPublishedZoomRatio = ratio
            onZoomChanged?.invoke(ratio, zoomMinRatio, zoomMaxRatio)
        }

        /**
         * Submits one ratio to the latest-wins queue; duplicate consecutive
         * ratios are dropped so settling issues no redundant CameraX requests.
         */
        private fun submitZoom(ratio: Float) {
            if (closed || !ratio.isFinite()) {
                return
            }
            if (abs(ratio - lastSubmittedZoomRatio) < ZOOM_SUBMIT_EPSILON) {
                return
            }
            lastSubmittedZoomRatio = ratio
            val immediate = zoomRequests.submit(ratio)
            if (immediate != null) {
                applyZoomRequest(immediate)
            }
        }

        /** Applies one CameraX request at a time; the newest queued ratio goes next. */
        private fun applyZoomRequest(ratio: Float) {
            if (closed) {
                zoomRequests.reset()
                return
            }
            val future = runCatching { camera.cameraControl.setZoomRatio(ratio) }.getOrNull()
            if (future == null) {
                zoomRequests.reset()
                lastSubmittedZoomRatio = Float.NaN
                return
            }
            future.addListener({
                if (closed) {
                    zoomRequests.reset()
                    return@addListener
                }
                val next = zoomRequests.complete()
                if (next != null) {
                    applyZoomRequest(next)
                }
            }, mainExecutor)
        }

        /** Cancels the ticker and rejects every further request; call when the camera closes. */
        fun reset() {
            closed = true
            cancelTicker()
            tickerScope.cancel()
            zoomRequests.reset()
            lastSubmittedZoomRatio = Float.NaN
        }

        fun enableTorch() {
            torchEnabled = true
            camera.cameraControl.enableTorch(true)
        }

        fun disableTorch() {
            torchEnabled = false
            camera.cameraControl.enableTorch(false)
        }

        fun isHardwareTorchReallyEnabled(): Boolean {
            return camera.cameraInfo.torchState.value == TorchState.ON
        }

        fun hasTorchAvailable() = camera.cameraInfo.hasFlashUnit()

        companion object {
            const val DEFAULT_ZOOM_RATIO = 1f

            /** Gesture targets closer than this are already at the range limit. */
            private const val ZOOM_TARGET_EPSILON = 0.0005f

            /** Reports smaller than this to the UI are skipped between frames. */
            private const val ZOOM_STATE_PUBLISH_EPSILON = 0.001f

            /** Consecutive CameraX requests closer than this are duplicates. */
            private const val ZOOM_SUBMIT_EPSILON = 0.0005f

            /** ~60 Hz easing tick. */
            private const val ZOOM_TICK_MILLIS = 16L

            /** A stalled main thread must not make the easing jump in one step. */
            private const val ZOOM_TICK_MIN_STEP_MILLIS = 1f
            private const val ZOOM_TICK_MAX_STEP_MILLIS = 64f
        }
    }
}
