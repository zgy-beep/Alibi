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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.RECORDER_MEDIA_SELECTED_VALUE
import app.myzel394.alibi.ui.components.atoms.MessageBox
import app.myzel394.alibi.ui.components.atoms.MessageType
import app.myzel394.alibi.ui.models.PhotoCaptureModel

@OptIn(ExperimentalMaterial3Api::class)
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
        if (photoCapture.isInSession) {
            photoCapture.setMode(mode)
            if (mode == PhotoCaptureMode.BLACK_SCREEN) {
                onOpenBlackScreen()
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
            TopAppBar(
                title = { Text(stringResource(R.string.photo_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackNavigate) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.goBack),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.photo_setup_description),
                style = MaterialTheme.typography.bodyLarge,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Text(
                            stringResource(R.string.photo_settings_summary_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text(
                        stringResource(
                            R.string.photo_settings_summary_strategy,
                            stringResource(
                                when (settings.photoCaptureSettings
                                    .getEffectiveCaptureStrategy()
                                ) {
                                    app.myzel394.alibi.db.PhotoCaptureStrategy.BALANCED ->
                                        R.string.photo_settings_strategy_balanced
                                    app.myzel394.alibi.db.PhotoCaptureStrategy.FAST ->
                                        R.string.photo_settings_strategy_fast
                                    app.myzel394.alibi.db.PhotoCaptureStrategy.MOTION ->
                                        R.string.photo_settings_strategy_motion
                                }
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(
                            R.string.photo_settings_summary_feedback,
                            stringResource(
                                when (settings.photoCaptureSettings
                                    .getEffectiveHapticFeedback()
                                ) {
                                    app.myzel394.alibi.db.PhotoHapticFeedback.OFF ->
                                        R.string.photo_settings_feedback_off
                                    app.myzel394.alibi.db.PhotoHapticFeedback.LIGHT ->
                                        R.string.photo_settings_feedback_light
                                    app.myzel394.alibi.db.PhotoHapticFeedback.STANDARD ->
                                        R.string.photo_settings_feedback_standard
                                }
                            ),
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

            if (photoCapture.isInSession) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.photo_active_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(
                                if (photoCapture.state.mode == PhotoCaptureMode.BLACK_SCREEN) {
                                    R.string.photo_active_black
                                } else {
                                    R.string.photo_active_floating
                                }
                            )
                        )
                        Text(
                            stringResource(
                                when (photoCapture.state.status) {
                                    PhotoCaptureStatus.READY -> R.string.photo_black_ready
                                    PhotoCaptureStatus.CAPTURING -> R.string.photo_black_shooting
                                    PhotoCaptureStatus.SAVING,
                                    PhotoCaptureStatus.STOPPING -> R.string.photo_black_saving
                                    PhotoCaptureStatus.STARTING -> R.string.photo_black_starting
                                    PhotoCaptureStatus.ERROR -> R.string.photo_black_error
                                    else -> R.string.photo_active_description
                                }
                            )
                        )
                        if (photoCapture.state.mode == PhotoCaptureMode.FLOATING &&
                            photoCapture.state.status == PhotoCaptureStatus.READY
                        ) {
                            Text(
                                stringResource(R.string.photo_mode_active_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (photoCapture.state.status == PhotoCaptureStatus.ERROR) {
                            Text(
                                photoCapture.state.error
                                    ?: stringResource(R.string.photo_error_generic),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (photoCapture.state.canRetrySave) {
                                Button(onClick = photoCapture::retryLastSave) {
                                    Text(stringResource(R.string.photo_action_retry_save))
                                }
                            }
                        }
                    }
                }
            }

            errorMessage?.let {
                MessageBox(
                    type = MessageType.ERROR,
                    message = it,
                )
            }

            ModeCard(
                title = stringResource(R.string.photo_mode_black_title),
                description = stringResource(R.string.photo_mode_black_description),
                icon = {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                },
                actionLabel = stringResource(
                    if (photoCapture.isInSession &&
                        photoCapture.state.mode == PhotoCaptureMode.BLACK_SCREEN
                    ) {
                        R.string.photo_action_open_black
                    } else {
                        R.string.photo_action_start
                    }
                ),
                onClick = { begin(PhotoCaptureMode.BLACK_SCREEN) },
            )

            ModeCard(
                title = stringResource(R.string.photo_mode_floating_title),
                description = stringResource(R.string.photo_mode_floating_description),
                icon = {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = null)
                },
                actionLabel = stringResource(R.string.photo_action_use_floating),
                onClick = { begin(PhotoCaptureMode.FLOATING) },
            )

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

            if (photoCapture.isInSession) {
                Button(
                    onClick = { photoCapture.stopSession() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.photo_action_stop))
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                icon()
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
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
