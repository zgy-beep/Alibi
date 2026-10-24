package app.myzel394.alibi.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.ui.theme.LocalAlibiExtendedColors


@Composable
fun MessageBox(
    modifier: Modifier = Modifier,
    type: MessageType,
    message: String,
    title: String? = null,
    density: VisualDensity = VisualDensity.COMFORTABLE,
) {
    val extendedColors = LocalAlibiExtendedColors.current
    val backgroundColor = when (type) {
        MessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
        MessageType.INFO -> MaterialTheme.colorScheme.tertiaryContainer
        MessageType.SUCCESS -> extendedColors.successContainer
        MessageType.WARNING -> extendedColors.warningContainer
        MessageType.SURFACE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (type) {
        MessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        MessageType.INFO -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageType.SUCCESS -> extendedColors.onSuccessContainer
        MessageType.WARNING -> extendedColors.onWarningContainer
        MessageType.SURFACE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .let {
                when (density) {
                    VisualDensity.COMFORTABLE -> it.padding(horizontal = 8.dp, vertical = 16.dp)
                    VisualDensity.DENSE -> it.padding(8.dp)
                    VisualDensity.COMPACT -> it.padding(8.dp)
                }
            }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (density == VisualDensity.COMFORTABLE || density == VisualDensity.DENSE) {
            Icon(
                imageVector = when (type) {
                    MessageType.ERROR -> Icons.Default.Error
                    MessageType.INFO -> Icons.Default.Info
                    MessageType.SURFACE -> Icons.Default.Info
                    MessageType.SUCCESS -> Icons.Default.Check
                    MessageType.WARNING -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.padding(16.dp)
            )
        }
        Column {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

}

enum class MessageType {
    ERROR,
    INFO,
    SURFACE,
    SUCCESS,
    WARNING,
}

enum class VisualDensity {
    COMPACT,
    COMFORTABLE,
    DENSE,
}
