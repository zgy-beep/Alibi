package app.myzel394.alibi.ui.components.atoms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.theme.AlibiMotion
import app.myzel394.alibi.ui.theme.LocalAlibiExtendedColors
import app.myzel394.alibi.ui.theme.LocalReduceMotion

enum class RuntimeStatus {
    IDLE,
    STARTING,
    RUNNING,
    PAUSED,
}

val LocalAlibiRuntimeStatus = compositionLocalOf { RuntimeStatus.IDLE }

@Composable
fun TitleWithRunningStatus(
    title: String,
    style: TextStyle = LocalTextStyle.current,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = style,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(10.dp))
        RunningStatusIndicator()
    }
}

@Composable
fun RunningStatusIndicator() {
    val status = LocalAlibiRuntimeStatus.current
    val reduceMotion = LocalReduceMotion.current
    val colors = LocalAlibiExtendedColors.current
    val description = stringResource(
        when (status) {
            RuntimeStatus.STARTING -> R.string.ui_runtime_starting_description
            RuntimeStatus.PAUSED -> R.string.ui_runtime_paused_description
            else -> R.string.ui_runtime_status_description
        }
    )
    val containerColor = when (status) {
        RuntimeStatus.PAUSED -> colors.warningContainer
        RuntimeStatus.STARTING -> androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
        else -> colors.successContainer
    }
    val dotColor = when (status) {
        RuntimeStatus.PAUSED -> colors.warning
        RuntimeStatus.STARTING -> androidx.compose.material3.MaterialTheme.colorScheme.secondary
        else -> colors.success
    }

    AnimatedVisibility(
        visible = status != RuntimeStatus.IDLE,
        enter = fadeIn(AlibiMotion.state(reduceMotion)) +
            expandHorizontally(AlibiMotion.state(reduceMotion)),
        exit = fadeOut(AlibiMotion.state(reduceMotion)) +
            shrinkHorizontally(AlibiMotion.state(reduceMotion)),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(containerColor, CircleShape)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
        }
    }
}
