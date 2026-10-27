package app.myzel394.alibi.ui.components.SettingsScreen.Tiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.atoms.AlibiSettingRow

@Composable
fun CustomNotificationTile(
    onNavigateToCustomRecordingNotifications: () -> Unit,
    settings: AppSettings,
) {
    val label = if (settings.notificationSettings == null)
        stringResource(R.string.ui_settings_option_customNotification_description_setup)
    else stringResource(
        R.string.ui_settings_option_customNotification_description_edit
    )

    AlibiSettingRow(
        title = stringResource(R.string.ui_settings_option_customNotification_title),
        description = label,
        onClick = {
            onNavigateToCustomRecordingNotifications()
        },
    )
}
