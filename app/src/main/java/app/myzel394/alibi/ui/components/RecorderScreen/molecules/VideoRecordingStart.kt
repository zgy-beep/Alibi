package app.myzel394.alibi.ui.components.RecorderScreen.molecules

import android.Manifest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.BigButton
import app.myzel394.alibi.ui.components.atoms.PermissionRequester
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.utils.PermissionHelper

@Composable
fun VideoRecordingStart(
    videoRecorder: VideoRecorderModel,
    appSettings: AppSettings,
    onHideAudioRecording: () -> Unit,
    onShowAudioRecording: () -> Unit,
    showPreview: Boolean,
    buttonSize: Dp,
    compactLayout: Boolean = false,
) {
    val context = LocalContext.current

    var showSheet by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSheet) {
        VideoRecorderPreparationSheet(
            showPreview = showPreview,
            videoSettings = videoRecorder,
            onDismiss = {
                showSheet = false
            },
            onPreviewVisible = onHideAudioRecording,
            onPreviewHidden = onShowAudioRecording,
            onStartRecording = {
                videoRecorder.startRecording(context, appSettings)
            },
        )
    }

    PermissionRequester(
        permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
        onPermissionAvailable = {
            showSheet = true
        }
    ) { triggerExternalStorage ->
        BigButton(
            label = stringResource(R.string.recorder_home_video_label),
            description = stringResource(R.string.recorder_home_video_subtitle),
            accessibilityLabel = stringResource(R.string.ui_videoRecorder_action_start_label),
            icon = Icons.Default.Videocam,
            onLongClick = {
                if (appSettings.requiresExternalStoragePermission(context)) {
                    triggerExternalStorage()
                    return@BigButton
                }

                showSheet = true
            },
            onClick = {
                if (appSettings.requiresExternalStoragePermission(context)) {
                    triggerExternalStorage()
                    return@BigButton
                }

                if (PermissionHelper.hasGranted(
                        context,
                        Manifest.permission.CAMERA
                    ) && PermissionHelper.hasGranted(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    )
                ) {
                    videoRecorder.startRecording(context, appSettings)
                } else {
                    showSheet = true
                }
            },
            widthOverride = buttonSize,
            heightOverride = buttonSize * 1.36f,
            primary = true,
            compactLayout = compactLayout,
        )
    }
}
