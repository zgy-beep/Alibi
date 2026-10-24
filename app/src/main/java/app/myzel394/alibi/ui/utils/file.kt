package app.myzel394.alibi.ui.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface FileSaveResult {
    data class Success(val uri: Uri) : FileSaveResult
    data object Cancelled : FileSaveResult
    data class Failure(val error: Throwable) : FileSaveResult
}

@Composable
fun rememberFileSaverDialog(
    mimeType: String,
    callback: (FileSaveResult) -> Unit = {},
): ((File, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var file = remember { mutableStateOf<File?>(null) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) {
            val source = file.value
            file.value = null

            when {
                it == null -> callback(FileSaveResult.Cancelled)
                source == null -> callback(
                    FileSaveResult.Failure(IllegalStateException("Export source is unavailable"))
                )

                else -> scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                source.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            } ?: throw IllegalStateException("Could not open the export destination")
                            FileSaveResult.Success(it)
                        } catch (error: Exception) {
                            FileSaveResult.Failure(error)
                        }
                    }
                    callback(result)
                }
            }
        }

    return { it, name ->
        file.value = it
        launcher.launch(name ?: it.name)
    }
}

@Composable
fun rememberFileSelectorDialog(
    callback: (Uri) -> Unit
): ((String) -> Unit) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            if (it != null) {
                callback(it)
            }
        }

    return { mimeType ->
        launcher.launch(arrayOf(mimeType))
    }
}

@Composable
fun rememberFolderSelectorDialog(
    callback: (Uri?) -> Unit
): (() -> Unit) {
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) {
                val uri = it.data?.data

                callback(uri)
            }
        }

    return {
        launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        })
    }
}
