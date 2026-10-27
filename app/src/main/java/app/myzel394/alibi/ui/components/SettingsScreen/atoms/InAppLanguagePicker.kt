package app.myzel394.alibi.ui.components.SettingsScreen.atoms

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import app.myzel394.alibi.R
import app.myzel394.alibi.SUPPORTED_LOCALES
import app.myzel394.alibi.ui.components.atoms.AlibiSettingRow
import app.myzel394.alibi.ui.utils.IconResource
import com.maxkeppeker.sheets.core.models.base.ButtonStyle
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeker.sheets.core.models.base.SelectionButton
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import java.util.Locale

/**
 * Language row. Intended to be rendered inside an
 * [app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppLanguagePicker() {
    val showDialog = rememberUseCaseState()
    val locales = Locale.getAvailableLocales().filter {
        val locale = it.language +
                (if (it.country.isNotBlank()) "-${it.country}" else "") +
                (if (it.variant.isNotBlank()) "-${it.variant}" else "")

        SUPPORTED_LOCALES.contains(locale)
    }
    val currentLanguage = remember { currentLanguageLabel() }

    ListDialog(
        state = showDialog,
        header = Header.Default(
            title = stringResource(R.string.ui_settings_language_title),
            icon = IconSource(
                painter = IconResource.fromImageVector(Icons.Default.Translate).asPainterResource(),
                contentDescription = null,
            ),
        ),
        selection = ListSelection.Single(
            showRadioButtons = true,
            options = IntRange(0, locales.size - 1).map { index ->
                val locale = locales[index]!!

                ListOption(
                    titleText = locale.displayName,
                    subtitleText = locale.getDisplayName(Locale.ENGLISH),
                )
            }.toList(),
            positiveButton = SelectionButton(
                icon = IconSource(
                    painter = IconResource.fromImageVector(Icons.Default.CheckCircle)
                        .asPainterResource(),
                    contentDescription = null,
                ),
                text = stringResource(android.R.string.ok),
                type = ButtonStyle.TEXT,
            ),
        ) { index, _ ->
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(
                    locales[index]!!.toLanguageTag(),
                ),
            )
        },
    )

    AlibiSettingRow(
        title = stringResource(R.string.ui_settings_language_title),
        value = currentLanguage,
        onClick = showDialog::show,
    )
}

private fun currentLanguageLabel(): String {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val defaultLocale = Locale.getDefault()
    val locale = if (appLocales.isEmpty) {
        defaultLocale
    } else {
        appLocales.get(0) ?: defaultLocale
    }
    val language = locale.getDisplayLanguage(defaultLocale)
    if (locale == defaultLocale) {
        return language
    }
    val country = locale.getDisplayCountry(defaultLocale)
    return if (country.isBlank()) language else "$language ($country)"
}
