package app.myzel394.alibi.ui.components.RecorderScreen.organisms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.LowStorageInfo
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.BigButton
import app.myzel394.alibi.ui.components.RecorderScreen.molecules.AudioRecordingStart
import app.myzel394.alibi.ui.components.RecorderScreen.molecules.QuickMaxDurationSelector
import app.myzel394.alibi.ui.components.RecorderScreen.molecules.VideoRecordingStart
import app.myzel394.alibi.ui.effects.rememberForceUpdateOnLifeCycleChange
import app.myzel394.alibi.ui.models.AudioRecorderModel
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.theme.AlibiMotion
import app.myzel394.alibi.ui.theme.LocalAlibiExtendedColors
import app.myzel394.alibi.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun StartRecording(
    audioRecorder: AudioRecorderModel,
    videoRecorder: VideoRecorderModel,
    // Loading this from parent, because if we load it ourselves
    // and permissions have already been granted, initial
    // settings will be used, instead of the actual settings.
    appSettings: AppSettings,
    onSaveLastRecording: () -> Unit,
    onHideTopBar: () -> Unit,
    onShowTopBar: () -> Unit,
    showAudioRecorder: Boolean,
    onOpenPhotoCapture: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var showQuickMaxDurationSelector by rememberSaveable {
        mutableStateOf(false)
    }

    if (showQuickMaxDurationSelector) {
        QuickMaxDurationSelector(
            onDismiss = {
                showQuickMaxDurationSelector = false
            },
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
        val horizontalPadding = 20.dp
        val actionGap = 8.dp
        val compactLayout = maxWidth < 600.dp
        val actionWidth = minOf(
            116.dp,
            (maxWidth - horizontalPadding * 2 - actionGap * 2) / 3,
        )
        val forceUpdate = rememberForceUpdateOnLifeCycleChange()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
                .padding(top = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
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
                    onChangeDuration = {
                        showQuickMaxDurationSelector = true
                    },
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

                if (compactLayout) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        PhotoCaptureStartButton(
                            onClick = onOpenPhotoCapture,
                            buttonSize = actionWidth,
                            compactLayout = true,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (showAudioRecorder) {
                            AudioRecordingStart(
                                audioRecorder = audioRecorder,
                                appSettings = appSettings,
                                buttonSize = actionWidth,
                            )
                        }
                        VideoRecordingStart(
                            videoRecorder = videoRecorder,
                            appSettings = appSettings,
                            onHideAudioRecording = onHideTopBar,
                            onShowAudioRecording = onShowTopBar,
                            showPreview = !showAudioRecorder,
                            buttonSize = actionWidth,
                        )
                        PhotoCaptureStartButton(
                            onClick = onOpenPhotoCapture,
                            buttonSize = actionWidth,
                        )
                    }
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
private fun PhotoCaptureStartButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    compactLayout: Boolean = false,
) {
    BigButton(
        label = stringResource(R.string.photo_home_label),
        description = stringResource(R.string.recorder_home_photo_subtitle),
        accessibilityLabel = stringResource(R.string.photo_setup_title),
        icon = Icons.Default.PhotoCamera,
        onClick = onClick,
        widthOverride = buttonSize,
        heightOverride = buttonSize * 1.36f,
        compactLayout = compactLayout,
    )
}

@Composable
private fun CacheStatusCard(
    maxDuration: Long,
    onChangeDuration: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val progressTarget = (maxDuration / (60f * 60f * 1000f)).coerceIn(0.2f, 1f)
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = AlibiMotion.state(reduceMotion),
        label = "cacheDurationProgress",
    )
    val minutes = (maxDuration / 60_000L).coerceAtLeast(1L)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(148.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 6.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.ui_home_cache_duration_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.ui_home_cache_duration_value, minutes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = onChangeDuration,
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(
                            stringResource(R.string.ui_home_change_duration),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.ui_home_cache_ready_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.ui_home_cache_ready_message, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
