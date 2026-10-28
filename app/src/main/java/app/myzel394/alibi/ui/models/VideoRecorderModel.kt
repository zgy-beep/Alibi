package app.myzel394.alibi.ui.models

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.RecordingInformation
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.helpers.Doctor
import app.myzel394.alibi.helpers.VideoBatchesFolder
import app.myzel394.alibi.services.VideoRecorderService
import app.myzel394.alibi.ui.RECORDER_MEDIA_SELECTED_VALUE
import app.myzel394.alibi.ui.utils.CameraInfo
import app.myzel394.alibi.ui.utils.PermissionHelper

/**
 * Clamps a zoom ratio reported by the preview into the range supported by the
 * bound camera. Invalid or missing camera state falls back to the neutral 1.0x
 * range so a broken [androidx.camera.core.ZoomState] can never produce an
 * undefined zoom target.
 *
 * Pure function so the zoom target carry-over can be unit tested on the JVM.
 */
internal fun normalizeZoomRatio(ratio: Float, minRatio: Float, maxRatio: Float): Float {
    val min = minRatio.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_VIDEO_ZOOM_RATIO
    val max = maxRatio.takeIf { it.isFinite() && it >= min } ?: min
    val requested = ratio.takeIf { it.isFinite() } ?: DEFAULT_VIDEO_ZOOM_RATIO
    return requested.coerceIn(min, max)
}

const val DEFAULT_VIDEO_ZOOM_RATIO = 1f

class VideoRecorderModel :
    BaseRecorderModel<RecordingInformation, VideoBatchesFolder, VideoRecorderService>() {
    override var batchesFolder: VideoBatchesFolder? = null
    override val intentClass = VideoRecorderService::class.java

    var enableAudio by mutableStateOf(true)
    var cameraID by mutableIntStateOf(CameraInfo.Lens.BACK.androidValue)

    /** Zoom ratio chosen in the live preview; survives sheet dismissal. */
    var zoomRatio by mutableFloatStateOf(DEFAULT_VIDEO_ZOOM_RATIO)
        private set

    /** Zoom range reported by the currently bound camera, for the preview UI. */
    var zoomMinRatio by mutableFloatStateOf(DEFAULT_VIDEO_ZOOM_RATIO)
        private set
    var zoomMaxRatio by mutableFloatStateOf(DEFAULT_VIDEO_ZOOM_RATIO)
        private set

    /**
     * Live zoom state of the recorder service's own viewfinder while recording.
     * Kept separate from [zoomRatio] so the pre-record sheet zoom and the
     * in-recording pinch never overwrite each other. Updated from the service's
     * `onZoomChanged` (eased values) and reset when a recording starts.
     */
    var liveZoomRatio by mutableFloatStateOf(DEFAULT_VIDEO_ZOOM_RATIO)
        private set
    var liveZoomMinRatio by mutableFloatStateOf(DEFAULT_VIDEO_ZOOM_RATIO)
        private set
    var liveZoomMaxRatio by mutableFloatStateOf(DEFAULT_VIDEO_ZOOM_RATIO)
        private set

    /** `null` while no tap-to-focus result is pending, then the service result. */
    var focusResult by mutableStateOf<Boolean?>(null)

    // Delivered to the service once it (re)connects: the standard camera screen
    // may hand over its viewfinder before the service exists.
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var meteringPointFactory: MeteringPointFactory? = null

    override val isInRecording: Boolean
        get() = super.isInRecording

    var isStartingRecording by mutableStateOf(true)
        private set

    val cameraSelector: CameraSelector
        get() = CameraSelector.Builder().requireLensFacing(cameraID).build()

    fun init(context: Context) {
        enableAudio = PermissionHelper.hasGranted(context, Manifest.permission.RECORD_AUDIO)
        cameraID = CameraInfo.Lens.BACK.androidValue
    }

    /**
     * Called by the live preview on every pinch update. The ratio is carried
     * over to the recording service through [handleIntent].
     */
    fun onPreviewZoomChanged(ratio: Float, minRatio: Float, maxRatio: Float) {
        val min = minRatio.takeIf { it.isFinite() && it > 0f }
            ?: DEFAULT_VIDEO_ZOOM_RATIO
        val max = maxRatio.takeIf { it.isFinite() && it >= min } ?: min
        zoomMinRatio = min
        zoomMaxRatio = max
        zoomRatio = normalizeZoomRatio(ratio, min, max)
    }

    /**
     * The standard camera screen renders the recorder service's own preview into
     * its viewfinder: it hands over the `PreviewView.surfaceProvider`. Repeated
     * calls with the same provider are ignored, and a provider supplied before
     * the service connected is applied once the service is there. Clearing it
     * only detaches the viewfinder, the recording keeps running.
     */
    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        if (previewSurfaceProvider === provider) {
            return
        }
        previewSurfaceProvider = provider
        recorderService?.setPreviewSurfaceProvider(provider)
    }

    /**
     * Coordinate mapper for [focusAt], i.e. the viewfinder's
     * `PreviewView.getMeteringPointFactory()`. Cached like the surface provider so
     * it also works when it is supplied before the service connects.
     */
    fun setMeteringPointFactory(factory: MeteringPointFactory?) {
        if (meteringPointFactory === factory) {
            return
        }
        meteringPointFactory = factory
        recorderService?.setMeteringPointFactory(factory)
    }

    /**
     * Tap-to-focus at viewfinder pixel coordinates while recording. Clears the
     * previous result; the service reports the new one through [focusResult]
     * (or `false` when it cannot run the request).
     */
    fun focusAt(x: Float, y: Float) {
        focusResult = null
        val service = recorderService
        if (service == null) {
            focusResult = false
            return
        }
        service.focusAt(x, y)
    }

    /** Lets the UI hide the focus ring after it has shown the last result long enough. */
    fun clearFocusResult() {
        focusResult = null
    }

    /**
     * Relative pinch input while recording. The service eases it at camera level,
     * so both the live viewfinder and the recorded file follow the gesture.
     */
    fun zoomDuringRecording(factor: Float) {
        recorderService?.zoomBy(factor)
    }

    override fun startRecording(context: Context, settings: AppSettings) {
        // A new recording starts from the pre-record zoom; the service reports
        // the camera's real zoom range once its camera control is available.
        liveZoomRatio = DEFAULT_VIDEO_ZOOM_RATIO
        liveZoomMinRatio = DEFAULT_VIDEO_ZOOM_RATIO
        liveZoomMaxRatio = DEFAULT_VIDEO_ZOOM_RATIO
        focusResult = null

        batchesFolder = when (settings.saveFolder) {
            null -> VideoBatchesFolder.viaInternalFolder(context)
            RECORDER_MEDIA_SELECTED_VALUE -> VideoBatchesFolder.viaMediaFolder(context)
            else -> runCatching {
                VideoBatchesFolder.viaCustomFolder(
                    context,
                    DocumentFile.fromTreeUri(
                        context,
                        Uri.parse(settings.saveFolder)
                    )!!
                )
            }.getOrNull()
        }

        super.startRecording(context, settings)
    }

    override fun onServiceConnected(service: VideoRecorderService) {
        // `onServiceConnected` may be called when reconnecting to the service,
        // so we only want to actually start the recording if the service is idle and thus
        // not already recording
        if (service.state == RecorderState.IDLE) {
            isStartingRecording = true
            startServiceRecording(
                service = service,
                clearExisting = true,
                onFailure = { isStartingRecording = false },
            )
        } else {
            isStartingRecording = false
        }

        service.onZoomChanged = { ratio, minRatio, maxRatio ->
            liveZoomMinRatio = minRatio
            liveZoomMaxRatio = maxRatio
            liveZoomRatio = normalizeZoomRatio(ratio, minRatio, maxRatio)
        }
        service.onFocusChanged = { focused ->
            focusResult = focused
        }
        // Re-apply what the standard camera screen supplied before the service
        // connection existed (idempotent on the service side).
        service.setPreviewSurfaceProvider(previewSurfaceProvider)
        service.setMeteringPointFactory(meteringPointFactory)
        // The camera may already be bound when reconnecting to a running service.
        syncLiveZoom(service)

        service.onCameraControlAvailable = {
            isStartingRecording = false
            syncLiveZoom(service)
        }

        recorderState = service.state
        recordingTime = service.recordingTime
    }

    /** Mirrors the service's zoom state into the Compose state the viewfinder reads. */
    private fun syncLiveZoom(service: VideoRecorderService) {
        val control = service.cameraControl ?: return
        val min = control.zoomMinRatio
        val max = control.zoomMaxRatio
        liveZoomMinRatio = min
        liveZoomMaxRatio = max
        liveZoomRatio = normalizeZoomRatio(service.cameraZoomRatio, min, max)
    }

    override fun reset() {
        val service = recorderService
        service?.onZoomChanged = null
        service?.onFocusChanged = null
        liveZoomRatio = DEFAULT_VIDEO_ZOOM_RATIO
        liveZoomMinRatio = DEFAULT_VIDEO_ZOOM_RATIO
        liveZoomMaxRatio = DEFAULT_VIDEO_ZOOM_RATIO
        focusResult = null
        previewSurfaceProvider = null
        meteringPointFactory = null
        super.reset()
    }

    override fun handleIntent(intent: Intent) =
        intent.apply {
            putExtra("cameraID", cameraID)
            putExtra("enableAudio", enableAudio)
            putExtra("initialZoom", zoomRatio)
        }
}
