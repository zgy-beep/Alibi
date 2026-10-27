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
fun AudioRecorderShowAllMicrophonesTile(
    settings: AppSettings,
) {
    val scope = rememberCoroutineScope()
    val dataStore = LocalContext.current.dataStore

    fun updateValue(showAllMicrophones: Boolean) {
        scope.launch {
            dataStore.updateData {
                it.setAudioRecorderSettings(
                    it.audioRecorderSettings.setShowAllMicrophones(showAllMicrophones)
                )
            }
        }
    }


    AlibiSwitchSetting(
        title = stringResource(R.string.ui_settings_option_showAllMicrophones_title),
        description = stringResource(R.string.ui_settings_option_showAllMicrophones_description),
        checked = settings.audioRecorderSettings.showAllMicrophones,
        onCheckedChange = ::updateValue,
    )
}
