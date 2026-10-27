package app.myzel394.alibi.ui.components.SettingsScreen.Tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppLockSettings
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.helpers.AppLockHelper
import app.myzel394.alibi.ui.components.atoms.AlibiSwitchSetting
import app.myzel394.alibi.ui.components.atoms.MessageBox
import app.myzel394.alibi.ui.components.atoms.MessageType
import app.myzel394.alibi.ui.components.atoms.VisualDensity
import kotlinx.coroutines.launch

@Composable
fun EnableAppLockTile(
    settings: AppSettings,
) {
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val dataStore = context.dataStore

    val appLockSupport = AppLockHelper.getSupportType(context)

    if (appLockSupport === AppLockHelper.SupportType.UNAVAILABLE) {
        return
    }

    val authenticationTitle = stringResource(R.string.identityVerificationRequired_title)
    val authenticationSubtitle = stringResource(R.string.identityVerificationRequired_subtitle)

    AlibiSwitchSetting(
        title = stringResource(R.string.ui_settings_option_enableAppLock_title),
        description = stringResource(R.string.ui_settings_option_enableAppLock_description),
        checked = settings.isAppLockEnabled(),
        enabled = appLockSupport === AppLockHelper.SupportType.AVAILABLE,
        onCheckedChange = {
            scope.launch {
                val authenticationSuccessful = AppLockHelper.authenticate(
                    context,
                    title = authenticationTitle,
                    subtitle = authenticationSubtitle,
                ).await()

                if (!authenticationSuccessful) {
                    return@launch
                }

                dataStore.updateData {
                    it.setAppLockSettings(
                        if (it.appLockSettings == null)
                            AppLockSettings.getDefaultInstance()
                        else
                            null
                    )
                }
            }
        },
        extra = {
            if (appLockSupport === AppLockHelper.SupportType.NONE_ENROLLED) {
                MessageBox(
                    type = MessageType.WARNING,
                    message = stringResource(R.string.ui_settings_option_enableAppLock_enrollmentRequired),
                    density = VisualDensity.COMPACT,
                )
            }
        },
    )
}
