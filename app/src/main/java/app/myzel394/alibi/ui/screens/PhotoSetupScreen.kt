package app.myzel394.alibi.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.PhotoCaptureSettings
import app.myzel394.alibi.db.PhotoCaptureStrategy
import app.myzel394.alibi.db.PhotoHapticFeedback
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.RECORDER_MEDIA_SELECTED_VALUE
import app.myzel394.alibi.ui.components.atoms.AlibiSectionHeader
import app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import app.myzel394.alibi.ui.components.atoms.MessageBox
import app.myzel394.alibi.ui.components.atoms.MessageType
import app.myzel394.alibi.ui.components.atoms.SelectableModeCard
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.theme.AlibiLayout

@Composable
fun PhotoSetupScreen(
    photoCapture: PhotoCaptureModel,
    settings: AppSettings,
    onBackNavigate: () -> Unit,
    onOpenBlackScreen: () -> Unit,
    onOpenPhotoSettings: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    var selectedMode by rememberSaveable {
        mutableStateOf(
            if (photoCapture.isInSession) {
                photoCapture.state.mode ?: PhotoCaptureMode.BLACK_SCREEN
            } else {
                PhotoCaptureMode.BLACK_SCREEN
            }
        )
    }
    var requestedMode by rememberSaveable {
        mutableStateOf<PhotoCaptureMode?>(null)
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun startSession(mode: PhotoCaptureMode) {
        errorMessage = null
        val started = photoCapture.startSession(context, settings, mode)
        requestedMode = null

        if (started && mode == PhotoCaptureMode.BLACK_SCREEN) {
            onOpenBlackScreen()
        } else if (started && mode == PhotoCaptureMode.FLOATING) {
            onBackNavigate()
        } else if (!started) {
            errorMessage = context.getString(R.string.photo_error_generic)
        }
    }

    fun continueAfterPermissions(mode: PhotoCaptureMode) {
        if (!hasCameraPermission(context, settings)) {
            errorMessage = context.getString(
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    R.string.photo_permission_camera_denied
                } else {
                    R.string.photo_permission_storage_denied
                }
            )
            return
        }
        startSession(mode)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val mode = requestedMode ?: return@rememberLauncherForActivityResult
        if (Settings.canDrawOverlays(context)) {
            continueAfterPermissions(mode)
        } else {
            errorMessage = context.getString(R.string.photo_permission_overlay_denied)
            requestedMode = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val mode = requestedMode ?: return@rememberLauncherForActivityResult
        if (!hasCameraPermission(context, settings)) {
            errorMessage = context.getString(
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    R.string.photo_permission_camera_denied
                } else {
                    R.string.photo_permission_storage_denied
                }
            )
            requestedMode = null
            return@rememberLauncherForActivityResult
        }
        if (mode == PhotoCaptureMode.FLOATING && !Settings.canDrawOverlays(context)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
            )
        } else {
            startSession(mode)
        }
    }

    fun begin(mode: PhotoCaptureMode) {
        selectedMode = mode
        if (photoCapture.isInSession) {
            errorMessage = null
            photoCapture.setMode(mode)
            if (mode == PhotoCaptureMode.BLACK_SCREEN) {
                onOpenBlackScreen()
            } else {
                onBackNavigate()
            }
            return
        }

        requestedMode = mode
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.CAMERA)
        }
        if (needsLegacyStoragePermission(settings) &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missingPermissions.isEmpty()) {
            if (mode == PhotoCaptureMode.FLOATING && !Settings.canDrawOverlays(context)) {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    )
                )
            } else {
                continueAfterPermissions(mode)
            }
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            AlibiTopAppBar(
                title = stringResource(R.string.photo_setup_title),
                onBack = onBackNavigate,
                actions = {
                    IconButton(onClick = onOpenPhotoSettings) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.photo_settings_open),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = AlibiLayout.pagePadding,
                end = AlibiLayout.pagePadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(AlibiLayout.itemGap),
        ) {
            item {
                Text(
                    text = stringResource(R.string.photo_setup_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            errorMessage?.let { message ->
                item {
                    MessageBox(type = MessageType.ERROR, message = message)
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.photo_setup_mode_title),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                SelectableModeCard(
                    title = stringResource(R.string.photo_mode_black_title),
                    description = stringResource(R.string.photo_mode_black_description),
                    icon = Icons.Default.CameraAlt,
                    selected = selectedMode == PhotoCaptureMode.BLACK_SCREEN,
                    onClick = { selectedMode = PhotoCaptureMode.BLACK_SCREEN },
                )
            }
            item {
                SelectableModeCard(
                    title = stringResource(R.string.photo_mode_floating_title),
                    description = stringResource(R.string.photo_mode_floating_description),
                    icon = Icons.Default.PictureInPictureAlt,
                    selected = selectedMode == PhotoCaptureMode.FLOATING,
                    onClick = { selectedMode = PhotoCaptureMode.FLOATING },
                )
            }

            if (photoCapture.isInSession) {
                item {
                    ActiveSessionCard(photoCapture)
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.photo_settings_summary_title),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                AlibiSectionPanel {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.photo_settings_summary_strategy,
                                stringResource(settings.photoCaptureSettings.captureStrategyLabel()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.photo_settings_summary_feedback,
                                stringResource(settings.photoCaptureSettings.hapticLabel()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(
                            onClick = onOpenPhotoSettings,
                            enabled = !photoCapture.isInSession,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Text(
                                stringResource(R.string.photo_settings_open),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { begin(selectedMode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    Icon(
                        if (selectedMode == PhotoCaptureMode.BLACK_SCREEN) {
                            Icons.Default.CameraAlt
                        } else {
                            Icons.Default.PictureInPictureAlt
                        },
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(
                            when {
                                photoCapture.isInSession &&
                                    photoCapture.state.mode == selectedMode &&
                                    selectedMode == PhotoCaptureMode.BLACK_SCREEN ->
                                    R.string.photo_action_open_black
                                selectedMode == PhotoCaptureMode.FLOATING ->
                                    R.string.photo_setup_start_floating
                                else -> R.string.photo_setup_start_black
                            }
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(
                        stringResource(R.string.photo_action_open_gallery),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (photoCapture.isInSession) {
                item {
                    FilledTonalButton(
                        onClick = photoCapture::stopSession,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.photo_action_stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(photoCapture: PhotoCaptureModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.photo_active_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(
                    if (photoCapture.state.mode == PhotoCaptureMode.BLACK_SCREEN) {
                        R.string.photo_active_black
                    } else {
                        R.string.photo_active_floating
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(photoCapture.state.status.label()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (photoCapture.state.status == PhotoCaptureStatus.ERROR) {
                Text(
                    photoCapture.state.error ?: stringResource(R.string.photo_error_generic),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (photoCapture.state.canRetrySave) {
                    FilledTonalButton(onClick = photoCapture::retryLastSave) {
                        Text(stringResource(R.string.photo_action_retry_save))
                    }
                }
            }
        }
    }
}

private fun PhotoCaptureSettings.captureStrategyLabel(): Int =
    when (getEffectiveCaptureStrategy()) {
        PhotoCaptureStrategy.BALANCED -> R.string.photo_settings_strategy_balanced
        PhotoCaptureStrategy.FAST -> R.string.photo_settings_strategy_fast
        PhotoCaptureStrategy.MOTION -> R.string.photo_settings_strategy_motion
    }

private fun PhotoCaptureSettings.hapticLabel(): Int =
    when (getEffectiveHapticFeedback()) {
        PhotoHapticFeedback.OFF -> R.string.photo_settings_feedback_off
        PhotoHapticFeedback.LIGHT -> R.string.photo_settings_feedback_light
        PhotoHapticFeedback.STANDARD -> R.string.photo_settings_feedback_standard
    }

private fun PhotoCaptureStatus.label(): Int = when (this) {
    PhotoCaptureStatus.STOPPED -> R.string.photo_active_description
    PhotoCaptureStatus.READY -> R.string.photo_black_ready
    PhotoCaptureStatus.CAPTURING -> R.string.photo_black_shooting
    PhotoCaptureStatus.SAVING,
    PhotoCaptureStatus.STOPPING -> R.string.photo_black_saving
    PhotoCaptureStatus.STARTING -> R.string.photo_black_starting
    PhotoCaptureStatus.ERROR -> R.string.photo_black_error
}

private fun needsLegacyStoragePermission(settings: AppSettings): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        settings.saveFolder == RECORDER_MEDIA_SELECTED_VALUE

private fun hasCameraPermission(context: android.content.Context, settings: AppSettings): Boolean {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return !needsLegacyStoragePermission(settings) ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
}
