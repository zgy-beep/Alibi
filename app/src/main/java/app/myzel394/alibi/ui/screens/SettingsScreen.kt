package app.myzel394.alibi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.BuildConfig
import app.myzel394.alibi.R
import app.myzel394.alibi.helpers.AppLockHelper
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.CustomNotificationTile
import app.myzel394.alibi.ui.components.SettingsScreen.Tiles.EnableAppLockTile
import app.myzel394.alibi.ui.components.SettingsScreen.atoms.InAppLanguagePicker
import app.myzel394.alibi.ui.components.SettingsScreen.atoms.ThemeSelector
import app.myzel394.alibi.ui.components.atoms.AlibiSectionHeader
import app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel
import app.myzel394.alibi.ui.components.atoms.AlibiSettingRow
import app.myzel394.alibi.ui.components.atoms.AlibiSettingsNotice
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import app.myzel394.alibi.ui.effects.rememberSettings
import app.myzel394.alibi.ui.models.AudioRecorderModel
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.models.VideoRecorderModel

@Composable
fun SettingsScreen(
    onBackNavigate: () -> Unit,
    onNavigateToAudioSettings: () -> Unit,
    onNavigateToVideoSettings: () -> Unit,
    onNavigateToPhotoCaptureSettings: () -> Unit,
    onNavigateToStorageSettings: () -> Unit,
    onNavigateToCustomRecordingNotifications: () -> Unit,
    onNavigateToAboutScreen: () -> Unit,
    audioRecorder: AudioRecorderModel,
    videoRecorder: VideoRecorderModel,
    photoCapture: PhotoCaptureModel,
) {
    val context = LocalContext.current
    val settings = rememberSettings()
    val isRuntimeActive = audioRecorder.isInRecording ||
        videoRecorder.isInRecording ||
        photoCapture.isInSession
    val appLockSupport = AppLockHelper.getSupportType(context)

    Scaffold(
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

            // Section 1: Capture Modes
            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_modes_title),
                    description = stringResource(R.string.settings_section_modes_description),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiSettingRow(
                        title = stringResource(R.string.ui_settings_sections_audio_title),
                        description = stringResource(R.string.audio_settings_page_description),
                        onClick = onNavigateToAudioSettings,
                    )
                    HorizontalDivider()
                    AlibiSettingRow(
                        title = stringResource(R.string.ui_settings_sections_video_title),
                        description = stringResource(R.string.video_settings_page_description),
                        onClick = onNavigateToVideoSettings,
                    )
                    HorizontalDivider()
                    AlibiSettingRow(
                        title = stringResource(R.string.photo_settings_page_title),
                        description = stringResource(R.string.photo_settings_section_summary),
                        onClick = onNavigateToPhotoCaptureSettings,
                    )
                }
            }

            // Section 2: Preferences & System
            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_system_title),
                    description = stringResource(R.string.settings_section_system_description),
                )
            }
            item {
                AlibiSectionPanel {
                    ThemeSelector()
                    HorizontalDivider()
                    InAppLanguagePicker()
                    HorizontalDivider()
                    AlibiSettingRow(
                        title = stringResource(R.string.storage_settings_page_title),
                        description = stringResource(R.string.storage_settings_page_description),
                        onClick = onNavigateToStorageSettings,
                    )
                    HorizontalDivider()
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

            // Section 3: About
            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.settings_section_about),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiSettingRow(
                        title = stringResource(R.string.ui_about_title),
                        value = "v${BuildConfig.VERSION_NAME}",
                        onClick = onNavigateToAboutScreen,
                    )
                }
            }
        }
    }
}
