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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.helpers.AppLockHelper
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderBitrateTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderEncoderTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderOutputFormatTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderSamplingRateTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.AudioRecorderShowAllMicrophonesTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.CustomNotificationTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.DeleteRecordingsImmediatelyTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.EnableAppLockTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.FilenameFormatTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.ImportExport
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.IntervalDurationTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.MaxDurationTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.SaveFolderTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.VideoRecorderBitrateTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.VideoRecorderFrameRateTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.VideoRecorderQualityTile
import app.myzel394.alibi.ui.components.SettingsScreen.atoms.InAppLanguagePicker
import app.myzel394.alibi.ui.components.SettingsScreen.atoms.ThemeSelector
import app.myzel394.alibi.ui.components.atoms.AlibiSectionHeader
import app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel
import app.myzel394.alibi.ui.components.atoms.AlibiSettingRow
import app.myzel394.alibi.ui.components.atoms.AlibiSettingsNotice
import app.myzel394.alibi.ui.components.atoms.AlibiSwitchSetting
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import app.myzel394.alibi.ui.effects.rememberSettings
import app.myzel394.alibi.ui.models.AudioRecorderModel
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.models.VideoRecorderModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBackNavigate: () -> Unit,
    onNavigateToCustomRecordingNotifications: () -> Unit,
    onNavigateToAboutScreen: () -> Unit,
    onNavigateToPhotoCaptureSettings: () -> Unit,
    audioRecorder: AudioRecorderModel,
    videoRecorder: VideoRecorderModel,
    photoCapture: PhotoCaptureModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dataStore = context.dataStore
    val settings = rememberSettings()
    val isRuntimeActive = audioRecorder.isInRecording ||
        videoRecorder.isInRecording ||
        photoCapture.isInSession
    val appLockSupport = AppLockHelper.getSupportType(context)

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
                title = stringResource(R.string.ui_settings_title),
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
                    stringResource(R.string.settings_page_description),
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
                    title = stringResource(R.string.settings_appearance_theme),
                    description = stringResource(R.string.settings_appearance_description),
                )
            }
            item {
                AlibiSectionPanel {
                    ThemeSelector()
                    HorizontalDivider()
                    InAppLanguagePicker()
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_recording),
                    description = stringResource(R.string.settings_section_recording_description),
                )
            }
            item {
                AlibiSectionPanel {
                    MaxDurationTile(settings = settings)
                    HorizontalDivider()
                    IntervalDurationTile(settings = settings)
                    HorizontalDivider()
                    DeleteRecordingsImmediatelyTile(settings = settings)
                    HorizontalDivider()
                    FilenameFormatTile(
                        settings = settings,
                        snackbarHostState = snackbarHostState,
                    )
                    HorizontalDivider()
                    SaveFolderTile(
                        settings = settings,
                        snackbarHostState = snackbarHostState,
                        enabled = !photoCapture.isInSession,
                    )
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_notifications_security),
                    description = stringResource(
                        R.string.settings_section_notifications_security_description
                    ),
                )
            }
            item {
                AlibiSectionPanel {
                    CustomNotificationTile(
                        onNavigateToCustomRecordingNotifications = onNavigateToCustomRecordingNotifications,
                        settings = settings,
                    )
                    if (appLockSupport !== AppLockHelper.SupportType.UNAVAILABLE) {
                        HorizontalDivider()
                        EnableAppLockTile(settings = settings)
                    }
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_advanced),
                    description = stringResource(R.string.settings_section_advanced_description),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiSwitchSetting(
                        title = stringResource(R.string.ui_settings_advancedSettings_label),
                        description = stringResource(R.string.settings_advanced_description),
                        checked = settings.showAdvancedSettings,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                dataStore.updateData {
                                    it.setShowAdvancedSettings(enabled)
                                }
                            }
                        },
                    )
                }
            }

            if (settings.showAdvancedSettings) {
                item {
                    AlibiSectionHeader(
                        title = stringResource(R.string.ui_settings_sections_audio_title),
                        description = stringResource(R.string.ui_settings_sections_audio_description),
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

                item {
                    AlibiSectionHeader(
                        title = stringResource(R.string.ui_settings_sections_video_title),
                        description = stringResource(R.string.ui_settings_sections_video_description),
                    )
                }
                item {
                    AlibiSectionPanel {
                        VideoRecorderQualityTile(settings = settings)
                        HorizontalDivider()
                        VideoRecorderBitrateTile(settings = settings)
                        HorizontalDivider()
                        VideoRecorderFrameRateTile(settings = settings)
                    }
                }

                item {
                    AlibiSectionHeader(
                        title = stringResource(R.string.photo_settings_section_title),
                        description = stringResource(R.string.photo_settings_section_description),
                    )
                }
                item {
                    AlibiSectionPanel {
                        AlibiSettingRow(
                            title = stringResource(R.string.photo_settings_page_title),
                            description = stringResource(R.string.photo_settings_section_summary),
                            onClick = onNavigateToPhotoCaptureSettings,
                        )
                    }
                }

                item {
                    AlibiSectionHeader(
                        title = stringResource(R.string.settings_section_backup),
                        description = stringResource(R.string.settings_section_backup_description),
                    )
                }
                item {
                    AlibiSectionPanel {
                        ImportExport(
                            snackbarHostState = snackbarHostState,
                            enabled = !photoCapture.isInSession,
                        )
                    }
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_about),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiSettingRow(
                        title = stringResource(R.string.ui_about_title),
                        onClick = onNavigateToAboutScreen,
                    )
                }
            }
        }
    }
}
