package app.myzel394.alibi.services

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
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
                    camera = cameraProvider!!.bindToLifecycle(
                        this,
                        selectedCamera,
                        capture,
                    )

                    cameraControl = CameraControl(
                        camera = camera!!,
                        mainExecutor = ContextCompat.getMainExecutor(this@VideoRecorderService),
                    ).also {
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
            val capture = videoCapture
            runCatching {
                if (capture != null) {
                    cameraProvider?.unbind(capture)
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
            camera = null
            cameraReady = false
            pendingCycleStart = false
        }
    }

    override fun onDestroy() {
        stopActiveRecording()
        _videoFinalizerListener?.complete(Unit)
        _cameraCloserListener?.complete(Unit)
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
        const val CAMERA_CLOSE_TIMEOUT = 20000L
    }

    class CameraControl(
        val camera: Camera,
        private val mainExecutor: Executor,
        // Save state for optimistic updates
        var torchEnabled: Boolean = false,
    ) {
        private val zoomRequests = ZoomRequestQueue()

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
        }

        /**
         * Applies the zoom chosen in the preparation sheet, clamped into the
         * range the camera really supports.
         */
        fun applyInitialZoom(ratio: Float) {
            updateZoomBounds()
            val clamped = normalizeZoom(ratio)
            zoomRatio = clamped
            submitZoom(clamped)
        }

        fun setZoomRatio(target: Float) {
            updateZoomBounds()
            val clamped = normalizeZoom(target)
            if (abs(clamped - zoomRatio) < ZOOM_EPSILON) {
                return
            }
            zoomRatio = clamped
            submitZoom(clamped)
        }

        fun zoomBy(factor: Float) {
            if (!factor.isFinite() || factor <= 0f) {
                return
            }
            setZoomRatio(zoomRatio * factor)
        }

        private fun normalizeZoom(ratio: Float): Float {
            val requested = ratio.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_ZOOM_RATIO
            return requested.coerceIn(zoomMinRatio, zoomMaxRatio)
        }

        /** Marks the request queue dirty; call when the camera is closing. */
        fun reset() {
            zoomRequests.reset()
        }

        private fun submitZoom(ratio: Float) {
            val immediate = zoomRequests.submit(ratio)
            if (immediate != null) {
                applyZoomRequest(immediate)
            }
        }

        private fun applyZoomRequest(ratio: Float) {
            val future = runCatching { camera.cameraControl.setZoomRatio(ratio) }.getOrNull()
            if (future == null) {
                zoomRequests.reset()
                return
            }
            future.addListener({
                val next = zoomRequests.complete()
                if (next != null) {
                    applyZoomRequest(next)
                }
            }, mainExecutor)
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
            private const val ZOOM_EPSILON = 0.001f
        }
    }
}
