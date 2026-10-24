package app.myzel394.alibi.ui.components.RecorderScreen.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.ui.theme.AlibiMotion
import app.myzel394.alibi.ui.theme.LocalReduceMotion

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BigButton(
    label: String,
    icon: ImageVector,
    description: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    widthOverride: Dp? = null,
    heightOverride: Dp? = null,
    primary: Boolean = false,
    compactLayout: Boolean = false,
    accessibilityLabel: String = label,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = AlibiMotion.press(reduceMotion),
        label = "modeButtonScale",
    )
    val shape = MaterialTheme.shapes.large
    val containerColor = if (primary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (primary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val sizingModifier = if (compactLayout) {
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
    } else {
        when {
            widthOverride != null && heightOverride != null ->
                Modifier.width(widthOverride).height(heightOverride)

            widthOverride != null -> Modifier.width(widthOverride)
            heightOverride != null -> Modifier.height(heightOverride)
            else -> Modifier
        }
    }
    val surfaceModifier = modifier
        .then(sizingModifier)
        .scale(pressedScale)
        .clip(shape)
        .background(containerColor)
        .border(
            width = 1.dp,
            color = if (primary) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            shape = shape,
        )
        .semantics { contentDescription = accessibilityLabel }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(
                color = if (primary) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ),
            onClick = onClick,
            onLongClick = onLongClick,
        )

    val iconContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(if (compactLayout) 52.dp else 56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (primary) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = if (primary) contentColor else MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (compactLayout) {
        Row(
            modifier = surfaceModifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            iconContent()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 2,
                )
                description?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (primary) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = surfaceModifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            iconContent()
            Spacer(modifier = Modifier.height(ButtonDefaults.IconSpacing))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            description?.let {
                Spacer(modifier = Modifier.height(ButtonDefaults.IconSpacing))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (primary) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
