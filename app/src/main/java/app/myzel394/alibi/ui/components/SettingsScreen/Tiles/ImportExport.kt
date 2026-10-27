package app.myzel394.alibi.ui.components.SettingsScreen.Tiles

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.atoms.AlibiSettingRow
import app.myzel394.alibi.ui.utils.FileSaveResult
import app.myzel394.alibi.ui.utils.rememberFileSaverDialog
import app.myzel394.alibi.ui.utils.rememberFileSelectorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.nio.charset.StandardCharsets

@Composable
fun ImportExport(
    snackbarHostState: SnackbarHostState,
    enabled: Boolean = true,
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val dataStore = LocalContext.current.dataStore
    val settings = dataStore
        .data
        .collectAsState(initial = AppSettings.getDefaultInstance())
        .value

    var settingsToBeImported by remember { mutableStateOf<AppSettings?>(null) }
    val importFailureMessage = stringResource(R.string.settings_import_failed)
    val exportFailureMessage = stringResource(R.string.file_export_failed)

    val saveFile = rememberFileSaverDialog("application/json") { result ->
        if (result is FileSaveResult.Failure) {
            scope.launch { snackbarHostState.showSnackbar(exportFailureMessage) }
        }
    }
    val openFile = rememberFileSelectorDialog { uri ->
        scope.launch {
            try {
                settingsToBeImported = withContext(Dispatchers.IO) {
                    val rawContent = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readLimitedText(MAX_SETTINGS_BYTES)
                    } ?: throw IllegalStateException("Could not open the settings file")
                    AppSettings.fromExportedString(rawContent)
                }
            } catch (error: Exception) {
                settingsToBeImported = null
                snackbarHostState.showSnackbar(importFailureMessage)
            }
        }
    }

    if (settingsToBeImported != null) {
        val successMessage = stringResource(R.string.ui_settings_option_import_success)

        AlertDialog(
            onDismissRequest = {
                settingsToBeImported = null
            },
            title = {
                Text(stringResource(R.string.ui_settings_option_import_label))
            },
            text = {
                Text(stringResource(R.string.ui_settings_option_import_dialog_text))
            },
            icon = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!enabled) {
                            return@Button
                        }
                        val importedSettings = settingsToBeImported ?: return@Button
                        scope.launch {
                            dataStore.updateData {
                                importedSettings
                            }
                            settingsToBeImported = null

                            snackbarHostState.showSnackbar(
                                message = successMessage,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    enabled = enabled,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.ui_settings_option_import_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        settingsToBeImported = null
                    },
                ) {
                    Text(stringResource(R.string.dialog_close_cancel_label))
                }
            },
        )
    }

    AlibiSettingRow(
        title = stringResource(R.string.ui_settings_option_import_label),
        onClick = {
            openFile("application/json")
        },
        enabled = enabled,
    )
    HorizontalDivider()
    AlibiSettingRow(
        title = stringResource(R.string.ui_settings_option_export_label),
        onClick = {
            val settingsToExport = settings
            scope.launch {
                val tempFile = withContext(Dispatchers.IO) {
                    File.createTempFile("alibi_settings", ".json").also {
                        it.writeText(settingsToExport.exportToString())
                    }
                }
                saveFile(tempFile, "alibi_settings.json")
            }
        },
        enabled = enabled,
    )
}

private fun InputStream.readLimitedText(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) {
            break
        }
        total += read
        if (total > maxBytes) {
            throw IllegalArgumentException("The settings file is too large")
        }
        output.write(buffer, 0, read)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

private const val MAX_SETTINGS_BYTES = 1024 * 1024
