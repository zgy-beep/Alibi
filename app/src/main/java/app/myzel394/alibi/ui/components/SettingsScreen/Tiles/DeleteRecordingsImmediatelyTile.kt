package app.myzel394.alibi.ui.components.SettingsScreen.Tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.atoms.AlibiSwitchSetting
import kotlinx.coroutines.launch

@Composable
fun DeleteRecordingsImmediatelyTile(
    settings: AppSettings,
) {
    val scope = rememberCoroutineScope()

    val dataStore = LocalContext.current.dataStore

    AlibiSwitchSetting(
        title = stringResource(R.string.ui_settings_option_deleteRecordingsImmediately_title),
        description = stringResource(R.string.ui_settings_option_deleteRecordingsImmediately_description),
        checked = settings.deleteRecordingsImmediately,
        onCheckedChange = {
            scope.launch {
                dataStore.updateData {
                    it.setDeleteRecordingsImmediately(it.deleteRecordingsImmediately.not())
                }
            }
        },
    )
}
