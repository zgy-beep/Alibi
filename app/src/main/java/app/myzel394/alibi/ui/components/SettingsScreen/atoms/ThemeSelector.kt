package app.myzel394.alibi.ui.components.SettingsScreen.atoms

import android.os.Build
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.atoms.AlibiChoiceSetting
import app.myzel394.alibi.ui.components.atoms.AlibiSwitchSetting
import kotlinx.coroutines.launch

/**
 * Theme, dynamic color, and reduced motion. Intended to be rendered inside an
 * [app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel]; the rows are
 * separated with dividers so the panel matches the camera settings page.
 */
@Composable
fun ThemeSelector() {
    val scope = rememberCoroutineScope()
    val dataStore = LocalContext.current.dataStore
    val settings by dataStore.data.collectAsState(initial = AppSettings.getDefaultInstance())

    AlibiChoiceSetting(
        title = stringResource(R.string.settings_theme_title),
        description = stringResource(R.string.settings_theme_description),
        values = AppSettings.Theme.entries.toList(),
        selected = settings.theme,
        label = {
            stringResource(
                when (it) {
                    AppSettings.Theme.SYSTEM -> R.string.settings_theme_system
                    AppSettings.Theme.LIGHT -> R.string.settings_theme_light
                    AppSettings.Theme.DARK -> R.string.settings_theme_dark
                },
            )
        },
        onSelect = { theme ->
            scope.launch {
                dataStore.updateData { it.setTheme(theme) }
            }
        },
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        HorizontalDivider()
        AlibiSwitchSetting(
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

    HorizontalDivider()
    AlibiSwitchSetting(
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
