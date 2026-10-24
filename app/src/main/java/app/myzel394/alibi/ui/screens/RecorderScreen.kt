package app.myzel394.alibi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.RecordingInformation
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.components.RecorderScreen.organisms.AudioRecordingStatus
import app.myzel394.alibi.ui.components.RecorderScreen.organisms.RecorderEventsHandler
import app.myzel394.alibi.ui.components.RecorderScreen.organisms.StartRecording
import app.myzel394.alibi.ui.components.RecorderScreen.organisms.VideoRecordingStatus
import app.myzel394.alibi.ui.models.AudioRecorderModel
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.theme.LocalAlibiExtendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    onNavigateToSettingsScreen: () -> Unit,
    onNavigateToPhotoCapture: () -> Unit,
    audioRecorder: AudioRecorderModel,
    videoRecorder: VideoRecorderModel,
    photoCapture: PhotoCaptureModel,
    settings: AppSettings,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val extendedColors = LocalAlibiExtendedColors.current

    RecorderEventsHandler(
        settings = settings,
        snackbarHostState = snackbarHostState,
        audioRecorder = audioRecorder,
        videoRecorder = videoRecorder,
    )

    // TopAppBar and AudioRecordingStart should be hidden when
    // the video preview is visible.
    // We need to preview the video inline to
    // be able to capture the touch release event.
    var topBarVisible by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(
                        snackbarData = it,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        dismissActionContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            )
        },
        topBar = {
            if (topBarVisible)
                return@Scaffold TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.app_name))
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                shape = CircleShape,
                                color = extendedColors.successContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                extendedColors.success,
                                                CircleShape,
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.ui_home_local_status),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extendedColors.onSuccessContainer,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            IconButton(
                                onClick = onNavigateToSettingsScreen,
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.ui_settings_title)
                                )
                            }
                        }
                    }
                )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (audioRecorder.isInRecording)
                AudioRecordingStatus(audioRecorder = audioRecorder)
            else if (videoRecorder.isInRecording)
                VideoRecordingStatus(videoRecorder = videoRecorder)
            else if (photoCapture.isInSession)
                PhotoCaptureStatusPanel(
                    photoCapture = photoCapture,
                    onManage = onNavigateToPhotoCapture,
                )
            else
                StartRecording(
                    audioRecorder = audioRecorder,
                    videoRecorder = videoRecorder,
                    appSettings = settings,
                    onSaveLastRecording = {
                        scope.launch {
                            when (settings.lastRecording!!.type) {
                                RecordingInformation.Type.AUDIO ->
                                    audioRecorder.onRecordingSave(false)

                                RecordingInformation.Type.VIDEO ->
                                    videoRecorder.onRecordingSave(false)
                            }
                        }
                    },
                    showAudioRecorder = topBarVisible,
                    onOpenPhotoCapture = onNavigateToPhotoCapture,
                    onOpenSettings = onNavigateToSettingsScreen,
                    onHideTopBar = {
                        topBarVisible = false
                    },
                    onShowTopBar = {
                        topBarVisible = true
                    },
                )
        }
    }
}

@Composable
private fun PhotoCaptureStatusPanel(
    photoCapture: PhotoCaptureModel,
    onManage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.padding(12.dp),
        )
        Text(
            text = stringResource(R.string.photo_active_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(
                if (photoCapture.state.mode == PhotoCaptureMode.BLACK_SCREEN) {
                    R.string.photo_active_black
                } else {
                    R.string.photo_active_floating
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(
                when (photoCapture.state.status) {
                    PhotoCaptureStatus.STARTING -> R.string.photo_black_starting
                    PhotoCaptureStatus.CAPTURING -> R.string.photo_black_shooting
                    PhotoCaptureStatus.SAVING,
                    PhotoCaptureStatus.STOPPING -> R.string.photo_black_saving
                    PhotoCaptureStatus.ERROR -> R.string.photo_black_error
                    else -> R.string.photo_black_ready
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(
            onClick = onManage,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.photo_action_manage))
        }
    }
}
