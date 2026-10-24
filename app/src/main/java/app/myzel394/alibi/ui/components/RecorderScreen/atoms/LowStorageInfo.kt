package app.myzel394.alibi.ui.components.RecorderScreen.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.helpers.BatchesFolder
import app.myzel394.alibi.helpers.VideoBatchesFolder
import app.myzel394.alibi.ui.theme.LocalAlibiExtendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StorageWarningState(
    val isLow: Boolean,
    val usesInternalFolder: Boolean,
)

@Composable
fun LowStorageInfo(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    val warningState by produceState<StorageWarningState?>(
        initialValue = null,
        appSettings.saveFolder,
        appSettings.maxDuration,
    ) {
        value = withContext(Dispatchers.IO) {
            val availableBytes = VideoBatchesFolder
                .importFromFolder(appSettings.saveFolder, context)
                .getAvailableBytes()
                ?: return@withContext null
            val requiredBytes =
                appSettings.maxDuration / 1000 / 60 *
                    BatchesFolder.requiredBytesForOneMinuteOfRecording(appSettings)

            StorageWarningState(
                isLow = availableBytes < requiredBytes * 1.1,
                usesInternalFolder = appSettings.saveFolder == null,
            )
        }
    }

    if (warningState?.isLow != true) {
        return
    }

    val extendedColors = LocalAlibiExtendedColors.current
    val contentColor = extendedColors.onWarningContainer
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = extendedColors.warningContainer,
        ),
        border = BorderStroke(1.dp, extendedColors.warning),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ui_home_storage_warning_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Text(
                    text = if (warningState!!.usesInternalFolder) {
                        stringResource(R.string.ui_home_storage_warning_internal)
                    } else {
                        stringResource(R.string.ui_home_storage_warning_message)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f),
                )
            }
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.warning,
                    contentColor = extendedColors.onWarning,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.ui_home_storage_warning_action),
                )
            }
        }
    }
}
