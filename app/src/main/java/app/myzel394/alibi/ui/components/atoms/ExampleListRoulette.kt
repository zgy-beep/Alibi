package app.myzel394.alibi.ui.components.atoms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wrapping quick-pick chips used below settings rows. Kept in one place so
 * every "suggested value" list uses the same chip shape, border, and spacing as
 * the rest of the settings surface. Unlike a horizontal scroller, wrapping
 * keeps every option fully visible and works with large font scales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun<T> ExampleListRoulette(
    items: List<T>,
    onItemSelected: (T) -> Unit,
    renderValue: @Composable (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onItemSelected(item) },
                label = { renderValue(item) },
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
