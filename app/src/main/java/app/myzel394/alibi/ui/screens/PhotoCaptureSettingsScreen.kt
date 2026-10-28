package app.myzel394.alibi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.camera.core.CameraSelector
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.PhotoAspectRatio
import app.myzel394.alibi.db.PhotoCaptureExtensionMode
import app.myzel394.alibi.db.PhotoCaptureSettings
import app.myzel394.alibi.db.PhotoCaptureStrategy
import app.myzel394.alibi.db.PhotoFlashMode
import app.myzel394.alibi.db.PhotoHapticFeedback
import app.myzel394.alibi.db.PhotoResolutionPreference
import app.myzel394.alibi.helpers.CameraExtensionSupport
import app.myzel394.alibi.helpers.queryCameraExtensionSupport
import app.myzel394.alibi.ui.effects.rememberSettings
import app.myzel394.alibi.ui.components.atoms.AlibiChoiceSetting
import app.myzel394.alibi.ui.components.atoms.AlibiSectionHeader
import app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel
import app.myzel394.alibi.ui.components.atoms.AlibiSwitchSetting
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureSettingsScreen(
    photoCapture: PhotoCaptureModel,
    onBackNavigate: () -> Unit,
) {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val scope = rememberCoroutineScope()
    val settings = rememberSettings()
    val photoSettings = settings.photoCaptureSettings
    val enabled = !photoCapture.isInSession
    var extensionSupport by remember { mutableStateOf<CameraExtensionSupport?>(null) }
    var extensionSupportChecked by remember { mutableStateOf(false) }
    LaunchedEffect(photoSettings.lensFacing) {
        extensionSupportChecked = false
        extensionSupport = queryCameraExtensionSupport(context, photoSettings.lensFacing)
        extensionSupportChecked = true
    }
    val extensionSupportText = when {
        !extensionSupportChecked ->
            stringResource(R.string.photo_settings_extension_support_checking)
        extensionSupport == null ->
            stringResource(R.string.photo_settings_extension_support_unknown)
        extensionSupport?.night == true && extensionSupport?.hdr == true ->
            stringResource(R.string.photo_settings_extension_support_night_hdr)
        extensionSupport?.night == true ->
            stringResource(R.string.photo_settings_extension_support_night)
        extensionSupport?.hdr == true ->
            stringResource(R.string.photo_settings_extension_support_hdr)
        else ->
            stringResource(R.string.photo_settings_extension_support_none)
    }

    fun update(transform: (PhotoCaptureSettings) -> PhotoCaptureSettings) {
        if (!enabled) return
        scope.launch {
            dataStore.updateData {
                it.setPhotoCaptureSettings(transform(it.photoCaptureSettings))
            }
        }
    }

    Scaffold(
        topBar = {
            AlibiTopAppBar(
                title = stringResource(R.string.photo_settings_page_title),
                onBack = onBackNavigate,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.photo_settings_page_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!enabled) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            stringResource(R.string.photo_settings_active_notice),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.photo_settings_capture_section),
                    description = stringResource(
                        R.string.photo_settings_capture_section_description
                    ),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_lens_title),
                        description = stringResource(R.string.photo_settings_lens_description),
                        values = listOf(
                            CameraSelector.LENS_FACING_BACK,
                            CameraSelector.LENS_FACING_FRONT,
                        ),
                        selected = photoSettings.lensFacing,
                        label = {
                            stringResource(
                                if (it == CameraSelector.LENS_FACING_FRONT) {
                                    R.string.photo_lens_front
                                } else {
                                    R.string.photo_lens_back
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setLensFacing(it) } },
                    )
                    HorizontalDivider()
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_strategy_title),
                        description = stringResource(
                            R.string.photo_settings_strategy_description
                        ),
                        values = PhotoCaptureStrategy.entries,
                        selected = photoSettings.getEffectiveCaptureStrategy(),
                        label = {
                            stringResource(
                                when (it) {
                                    PhotoCaptureStrategy.BALANCED ->
                                        R.string.photo_settings_strategy_balanced
                                    PhotoCaptureStrategy.FAST ->
                                        R.string.photo_settings_strategy_fast
                                    PhotoCaptureStrategy.MOTION ->
                                        R.string.photo_settings_strategy_motion
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setCaptureStrategy(it) } },
                    )
                    HorizontalDivider()
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_extension_title),
                        description = stringResource(
                            R.string.photo_settings_extension_description
                        ) + "\n" + extensionSupportText,
                        values = PhotoCaptureExtensionMode.entries,
                        selected = photoSettings.extensionMode,
                        label = {
                            stringResource(
                                when (it) {
                                    PhotoCaptureExtensionMode.AUTO ->
                                        R.string.photo_settings_extension_auto
                                    PhotoCaptureExtensionMode.OFF ->
                                        R.string.photo_settings_extension_off
                                    PhotoCaptureExtensionMode.NIGHT ->
                                        R.string.photo_settings_extension_night
                                    PhotoCaptureExtensionMode.HDR ->
                                        R.string.photo_settings_extension_hdr
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setExtensionMode(it) } },
                    )
                    HorizontalDivider()
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_resolution_title),
                        description = stringResource(
                            R.string.photo_settings_resolution_description
                        ),
                        values = PhotoResolutionPreference.entries,
                        selected = photoSettings.resolutionPreference,
                        label = {
                            stringResource(
                                when (it) {
                                    PhotoResolutionPreference.SPEED ->
                                        R.string.photo_settings_resolution_speed
                                    PhotoResolutionPreference.RECOMMENDED ->
                                        R.string.photo_settings_resolution_recommended
                                    PhotoResolutionPreference.QUALITY ->
                                        R.string.photo_settings_resolution_quality
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setResolutionPreference(it) } },
                    )
                    HorizontalDivider()
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_aspect_title),
                        description = stringResource(
                            R.string.photo_settings_aspect_description
                        ),
                        values = PhotoAspectRatio.entries,
                        selected = photoSettings.aspectRatio,
                        label = {
                            stringResource(
                                when (it) {
                                    PhotoAspectRatio.FOUR_THREE ->
                                        R.string.photo_settings_aspect_4_3
                                    PhotoAspectRatio.SIXTEEN_NINE ->
                                        R.string.photo_settings_aspect_16_9
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setAspectRatio(it) } },
                    )
                    HorizontalDivider()
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_flash_title),
                        description = stringResource(
                            R.string.photo_settings_flash_description
                        ),
                        values = PhotoFlashMode.entries,
                        selected = photoSettings.flashMode,
                        label = {
                            stringResource(
                                when (it) {
                                    PhotoFlashMode.OFF ->
                                        R.string.photo_settings_flash_off
                                    PhotoFlashMode.AUTO ->
                                        R.string.photo_settings_flash_auto
                                    PhotoFlashMode.ON ->
                                        R.string.photo_settings_flash_on
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setFlashMode(it) } },
                    )
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.photo_settings_focus_section),
                    description = stringResource(
                        R.string.photo_settings_focus_section_description
                    ),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiSwitchSetting(
                        title = stringResource(R.string.photo_settings_ois_title),
                        description = stringResource(R.string.photo_settings_ois_description),
                        checked = photoSettings.opticalStabilizationEnabled,
                        enabled = enabled,
                        onCheckedChange = { value ->
                            update { it.setOpticalStabilizationEnabled(value) }
                        },
                    )
                    HorizontalDivider()
                    AlibiSwitchSetting(
                        title = stringResource(R.string.photo_settings_caf_title),
                        description = stringResource(R.string.photo_settings_caf_description),
                        checked = photoSettings.continuousAutoFocusEnabled,
                        enabled = enabled,
                        onCheckedChange = { value ->
                            update { it.setContinuousAutoFocusEnabled(value) }
                        },
                    )
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.photo_settings_feedback_section),
                    description = stringResource(
                        R.string.photo_settings_feedback_section_description
                    ),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiChoiceSetting(
                        title = stringResource(R.string.photo_settings_feedback_title_new),
                        description = stringResource(
                            R.string.photo_settings_feedback_description_new
                        ),
                        values = PhotoHapticFeedback.entries,
                        selected = photoSettings.getEffectiveHapticFeedback(),
                        label = {
                            stringResource(
                                when (it) {
                                    PhotoHapticFeedback.OFF ->
                                        R.string.photo_settings_feedback_off
                                    PhotoHapticFeedback.LIGHT ->
                                        R.string.photo_settings_feedback_light
                                    PhotoHapticFeedback.STANDARD ->
                                        R.string.photo_settings_feedback_standard
                                }
                            )
                        },
                        enabled = enabled,
                        onSelect = { update { s -> s.setHapticFeedback(it) } },
                    )
                }
            }

            item {
                AlibiSectionHeader(
                    title = stringResource(R.string.photo_settings_interface_section),
                    description = stringResource(
                        R.string.photo_settings_interface_section_description
                    ),
                )
            }
            item {
                AlibiSectionPanel {
                    AlibiSwitchSetting(
                        title = stringResource(R.string.photo_settings_grid_title),
                        description = stringResource(R.string.photo_settings_grid_description),
                        checked = photoSettings.gridLinesEnabled,
                        enabled = enabled,
                        onCheckedChange = { value ->
                            update { it.setGridLinesEnabled(value) }
                        },
                    )
                    HorizontalDivider()
                    AlibiSwitchSetting(
                        title = stringResource(R.string.photo_settings_preview_launch_title),
                        description = stringResource(
                            R.string.photo_settings_preview_launch_description
                        ),
                        checked = photoSettings.showPreviewOnLaunch,
                        enabled = enabled,
                        onCheckedChange = { value ->
                            update { it.setShowPreviewOnLaunch(value) }
                        },
                    )
                    HorizontalDivider()
                    AlibiSwitchSetting(
                        title = stringResource(R.string.photo_settings_remember_zoom_title),
                        description = stringResource(
                            R.string.photo_settings_remember_zoom_description
                        ),
                        checked = photoSettings.rememberZoom,
                        enabled = enabled,
                        onCheckedChange = { value ->
                            update { it.setRememberZoom(value) }
                        },
                    )
                    HorizontalDivider()
                    AlibiSwitchSetting(
                        title = stringResource(R.string.photo_settings_mirror_title),
                        description = stringResource(R.string.photo_settings_mirror_description),
                        checked = photoSettings.mirrorFrontCamera,
                        enabled = enabled && photoSettings.lensFacing ==
                            CameraSelector.LENS_FACING_FRONT,
                        onCheckedChange = { value ->
                            update { it.setMirrorFrontCamera(value) }
                        },
                    )
                    HorizontalDivider()
                    SliderSetting(
                        title = stringResource(R.string.photo_settings_overlay_opacity_title),
                        description = stringResource(
                            R.string.photo_settings_overlay_opacity_description
                        ),
                        value = photoSettings.overlayOpacity,
                        enabled = enabled,
                        onCommit = { value ->
                            update { it.setOverlayOpacity(value) }
                        },
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        if (enabled) {
                            scope.launch {
                                dataStore.updateData {
                                    it.setPhotoCaptureSettings(PhotoCaptureSettings())
                                }
                            }
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Text(
                        stringResource(R.string.photo_settings_reset),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    description: String,
    value: Float,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    var draftValue by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = " ${(draftValue * 100).roundToInt()}%",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = draftValue,
            onValueChange = { draftValue = it },
            onValueChangeFinished = { onCommit(draftValue) },
            valueRange = PhotoCaptureSettings.MIN_OVERLAY_OPACITY..1f,
            enabled = enabled,
        )
    }
}
