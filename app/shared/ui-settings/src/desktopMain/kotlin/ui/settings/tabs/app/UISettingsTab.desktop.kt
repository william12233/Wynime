package com.wynime.app.ui.settings.tabs.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.intl.Locale
import com.wynime.app.data.models.preference.UISettings
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.SupportedLocales
import com.wynime.app.ui.lang.renderLocale
import com.wynime.app.ui.lang.settings_app_language
import com.wynime.app.ui.lang.settings_app_language_restart
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.components.DropdownItem
import com.wynime.app.ui.settings.framework.components.SettingsScope
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun SettingsScope.AppSettingsTabPlatform() {
}

@Composable
internal actual fun SettingsScope.LanguageSettingsPlatform(
    state: SettingsState<UISettings>,
) {
    val uiSettings by state

    DropdownItem(
        selected = { uiSettings.appLanguage },
        values = { listOf<Locale?>(null) + SupportedLocales },
        itemText = { Text(renderLocale(it)) },
        onSelect = {
            state.update(uiSettings.copy(appLanguage = it))
        },
        title = { Text(stringResource(Lang.settings_app_language)) },
        description = { Text(stringResource(Lang.settings_app_language_restart)) },
    )
}
