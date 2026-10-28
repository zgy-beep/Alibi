package app.myzel394.alibi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderBitrateTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderEncoderTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderOutputFormatTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderSamplingRateTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderShowAllMicrophonesTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.DeleteRecordingsImmediatelyTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.IntervalDurationTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.MaxDurationTile
import app.myzel394.alibi.ui.components.atoms.AlibiSectionHeader
import app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel
import app.myzel394.alibi.ui.components.atoms.AlibiSettingsNotice
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import app.myzel394.alibi.ui.effects.rememberSettings
import app.myzel394.alibi.ui.models.AudioRecorderModel

@Composable
fun AudioSettingsScreen(
    audioRecorder: AudioRecorderModel,
    onBackNavigate: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val settings = rememberSettings()
    val isRuntimeActive = audioRecorder.isInRecording

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
                },
            )
        },
        topBar = {
            AlibiTopAppBar(
                title = stringResource(R.string.audio_settings_page_title),
                onBack = onBackNavigate,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.audio_settings_page_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isRuntimeActive) {
                item {
                    AlibiSettingsNotice(
                        title = stringResource(R.string.ui_settings_hint_recordingActive_title),
                        message = stringResource(R.string.ui_settings_hint_recordingActive_message),
                    )
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.audio_settings_section_duration),
                    description = stringResource(R.string.audio_settings_section_duration_description),
                )
            }
            item {
                AlibiSectionPanel {
                    MaxDurationTile(settings = settings)
                    HorizontalDivider()
                    IntervalDurationTile(settings = settings)
                    HorizontalDivider()
                    DeleteRecordingsImmediatelyTile(settings = settings)
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.audio_settings_section_encoding),
                    description = stringResource(R.string.audio_settings_section_encoding_description),
                )
            }
            item {
                AlibiSectionPanel {
                    AudioRecorderShowAllMicrophonesTile(settings = settings)
                    HorizontalDivider()
                    AudioRecorderSamplingRateTile(settings = settings)
                    HorizontalDivider()
                    AudioRecorderEncoderTile(
                        snackbarHostState = snackbarHostState,
                        settings = settings,
                    )
                    HorizontalDivider()
                    AudioRecorderOutputFormatTile(settings = settings)
                    HorizontalDivider()
                    AudioRecorderBitrateTile(settings = settings)
                }
            }
        }
    }
}
