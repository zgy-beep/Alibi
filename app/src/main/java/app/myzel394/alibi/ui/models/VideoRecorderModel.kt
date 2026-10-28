package app.myzel394.alibi.ui.models

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.core.CameraSelector
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

    override fun startRecording(context: Context, settings: AppSettings) {
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

        service.onCameraControlAvailable = {
            isStartingRecording = false
        }

        recorderState = service.state
        recordingTime = service.recordingTime
    }

    override fun handleIntent(intent: Intent) =
        intent.apply {
            putExtra("cameraID", cameraID)
            putExtra("enableAudio", enableAudio)
            putExtra("initialZoom", zoomRatio)
        }
}
