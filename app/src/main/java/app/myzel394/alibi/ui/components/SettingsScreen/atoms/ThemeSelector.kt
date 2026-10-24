package app.myzel394.alibi.ui.components.SettingsScreen.atoms

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import kotlinx.coroutines.launch

@Composable
fun ThemeSelector() {
    val scope = rememberCoroutineScope()
    val dataStore = LocalContext.current.dataStore
    val settings by dataStore.data.collectAsState(initial = AppSettings.getDefaultInstance())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_theme),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppSettings.Theme.entries.forEach { theme ->
                val selected = settings.theme == theme
                FilterChip(
                    selected = selected,
                    onClick = {
                        scope.launch {
                            dataStore.updateData { it.setTheme(theme) }
                        }
                    },
                    label = {
                        Text(
                            when (theme) {
                                AppSettings.Theme.SYSTEM ->
                                    stringResource(R.string.settings_theme_system)

                                AppSettings.Theme.LIGHT ->
                                    stringResource(R.string.settings_theme_light)

                                AppSettings.Theme.DARK ->
                                    stringResource(R.string.settings_theme_dark)
                            },
                        )
                    },
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingSwitch(
                title = stringResource(R.string.settings_dynamic_color),
                description = stringResource(R.string.settings_dynamic_color_description),
                checked = settings.dynamicColorsEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        dataStore.updateData { it.setDynamicColorsEnabled(enabled) }
                    }
                },
            )
        }
        SettingSwitch(
            title = stringResource(R.string.settings_reduce_motion),
            description = stringResource(R.string.settings_reduce_motion_description),
            checked = settings.reduceMotionEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    dataStore.updateData { it.setReduceMotionEnabled(enabled) }
                }
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$title. $description" }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
