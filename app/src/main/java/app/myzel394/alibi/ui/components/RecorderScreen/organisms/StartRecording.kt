package app.myzel394.alibi.ui.components.RecorderScreen.organisms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.BigButton
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.LowStorageInfo
import app.myzel394.alibi.ui.components.RecorderScreen.molecules.AudioRecordingStart
import app.myzel394.alibi.ui.components.RecorderScreen.molecules.QuickMaxDurationSelector
import app.myzel394.alibi.ui.components.RecorderScreen.molecules.VideoRecordingStart
import app.myzel394.alibi.ui.effects.rememberForceUpdateOnLifeCycleChange
import app.myzel394.alibi.ui.models.AudioRecorderModel
import app.myzel394.alibi.ui.models.VideoRecorderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StartRecording(
    audioRecorder: AudioRecorderModel,
    videoRecorder: VideoRecorderModel,
    appSettings: AppSettings,
    onSaveLastRecording: () -> Unit,
    onHideTopBar: () -> Unit,
    onShowTopBar: () -> Unit,
    showAudioRecorder: Boolean,
    onOpenPhotoCapture: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var showQuickMaxDurationSelector by rememberSaveable { mutableStateOf(false) }

    if (showQuickMaxDurationSelector) {
        QuickMaxDurationSelector(
            onDismiss = { showQuickMaxDurationSelector = false },
        )
    }

    BoxWithConstraints {
        val lastRecordingAvailable by produceState(
            initialValue = false,
            appSettings.lastRecording,
        ) {
            value = withContext(Dispatchers.IO) {
                appSettings.lastRecording?.hasRecordingsAvailable(context) == true
            }
        }
        val actionWidth = maxWidth
        val horizontalPadding = if (maxWidth < 600.dp) 20.dp else 24.dp
        val forceUpdate = rememberForceUpdateOnLifeCycleChange()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 8.dp)
                    .then(forceUpdate),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LowStorageInfo(
                    modifier = Modifier.fillMaxWidth(),
                    appSettings = appSettings,
                    onAction = onOpenSettings,
                )

                CacheStatusCard(
                    maxDuration = appSettings.maxDuration,
                    onChangeDuration = { showQuickMaxDurationSelector = true },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ui_home_actions_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (lastRecordingAvailable) {
                        TextButton(onClick = onSaveLastRecording) {
                            Text(stringResource(R.string.ui_home_history_label))
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showAudioRecorder) {
                        AudioRecordingStart(
                            audioRecorder = audioRecorder,
                            appSettings = appSettings,
                            buttonSize = actionWidth,
                            compactLayout = true,
                        )
                    }
                    VideoRecordingStart(
                        videoRecorder = videoRecorder,
                        appSettings = appSettings,
                        onHideAudioRecording = onHideTopBar,
                        onShowAudioRecording = onShowTopBar,
                        showPreview = !showAudioRecorder,
                        buttonSize = actionWidth,
                        compactLayout = true,
                    )
                    PhotoCaptureStartButton(onClick = onOpenPhotoCapture)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_home_local_only_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoCaptureStartButton(onClick: () -> Unit) {
    BigButton(
        label = stringResource(R.string.photo_home_label),
        description = stringResource(R.string.recorder_home_photo_subtitle),
        accessibilityLabel = stringResource(R.string.photo_setup_title),
        icon = Icons.Default.PhotoCamera,
        onClick = onClick,
        primary = true,
        compactLayout = true,
    )
}

@Composable
private fun CacheStatusCard(
    maxDuration: Long,
    onChangeDuration: () -> Unit,
) {
    val progressTarget = (maxDuration / (60f * 60f * 1000f)).coerceIn(0.2f, 1f)
    val minutes = (maxDuration / 60_000L).coerceAtLeast(1L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChangeDuration),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_home_cache_duration_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.ui_home_cache_duration_value, minutes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(R.string.ui_home_change_duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { progressTarget },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = stringResource(R.string.ui_home_cache_ready_message, minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
