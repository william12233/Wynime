package com.wynime.app.ui.settings.tabs.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.theme.isPlatformSupportDynamicTheme
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_theme_always_dark_episode
import com.wynime.app.ui.lang.settings_theme_always_dark_episode_description
import com.wynime.app.ui.lang.settings_theme_animated_gradient_subject
import com.wynime.app.ui.lang.settings_theme_animated_gradient_subject_description
import com.wynime.app.ui.lang.settings_theme_dynamic_colors
import com.wynime.app.ui.lang.settings_theme_dynamic_colors_description
import com.wynime.app.ui.lang.settings_theme_dynamic_subject
import com.wynime.app.ui.lang.settings_theme_dynamic_subject_description
import com.wynime.app.ui.lang.settings_theme_frosted_glass
import com.wynime.app.ui.lang.settings_theme_frosted_glass_description
import com.wynime.app.ui.lang.settings_theme_high_contrast
import com.wynime.app.ui.lang.settings_theme_high_contrast_description
import com.wynime.app.ui.lang.settings_theme_palette
import com.wynime.app.ui.lang.settings_theme_title
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.SwitchItem
import com.wynime.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.ThemeGroup(
    state: SettingsState<ThemeSettings>,
) {
    val themeSettings by state

    Group(
        title = { Text(stringResource(Lang.settings_theme_title)) },
    ) {
        DarkModeSelectPanel(
            currentMode = themeSettings.darkMode,
            onModeSelected = { state.update(themeSettings.copy(darkMode = it)) },
            modifier = Modifier.padding(vertical = SettingsScope.itemVerticalSpacing),
        )

        if (isPlatformSupportDynamicTheme()) {
            SwitchItem(
                checked = themeSettings.useDynamicTheme,
                onCheckedChange = { checked ->
                    state.update(themeSettings.copy(useDynamicTheme = checked))
                },
                title = { Text(stringResource(Lang.settings_theme_dynamic_colors)) },
                description = { Text(stringResource(Lang.settings_theme_dynamic_colors_description)) },
            )
        }

        SwitchItem(
            checked = themeSettings.useBlackBackground,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(useBlackBackground = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_high_contrast)) },
            description = { Text(stringResource(Lang.settings_theme_high_contrast_description)) },
        )

        SwitchItem(
            checked = themeSettings.alwaysDarkInEpisodePage,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(alwaysDarkInEpisodePage = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_always_dark_episode)) },
            description = { Text(stringResource(Lang.settings_theme_always_dark_episode_description)) },
        )

        SwitchItem(
            checked = themeSettings.useDynamicSubjectPageTheme,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(useDynamicSubjectPageTheme = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_dynamic_subject)) },
            description = { Text(stringResource(Lang.settings_theme_dynamic_subject_description)) },
        )

        SwitchItem(
            checked = themeSettings.enableAnimatedGradientSubjectPage,
            onCheckedChange = { checked ->
                state.update(themeSettings.copy(enableAnimatedGradientSubjectPage = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_animated_gradient_subject)) },
            description = { Text(stringResource(Lang.settings_theme_animated_gradient_subject_description)) },
        )

        if (LocalPlatform.current.isMobile()) {
            SwitchItem(
                checked = themeSettings.enableFrostedGlassEffect,
                onCheckedChange = { checked ->
                    state.update(themeSettings.copy(enableFrostedGlassEffect = checked))
                },
                title = { Text(stringResource(Lang.settings_theme_frosted_glass)) },
                description = { Text(stringResource(Lang.settings_theme_frosted_glass_description)) },
            )
        }
    }

    Box(
        modifier = Modifier.alpha(if (themeSettings.useDynamicTheme) 0.5f else 1f),
    ) {
        Group(title = { Text(stringResource(Lang.settings_theme_palette)) }) {
            ThemePalette(
                selectedColor = themeSettings.seedColor.takeUnless { themeSettings.useDynamicTheme },
                onSelect = { color ->
                    state.update(themeSettings.copy(seedColorValue = color.value, useDynamicTheme = false))
                },
            )
        }
    }
}
