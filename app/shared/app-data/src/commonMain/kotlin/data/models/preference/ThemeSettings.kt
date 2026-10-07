package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.app.ui.theme.DefaultSeedColor

@Serializable
enum class DarkMode {
    AUTO, LIGHT, DARK,
}

@Serializable
@Immutable
data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDynamicTheme: Boolean = false,

    val useBlackBackground: Boolean = false,
    val alwaysDarkInEpisodePage: Boolean = false,
    val useDynamicSubjectPageTheme: Boolean = false,
    val seedColorValue: ULong = DefaultSeedColor.value,
    val enableAnimatedGradientSubjectPage: Boolean = false,
    val enableFrostedGlassEffect: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    @Transient
    val seedColor: Color = Color(seedColorValue).let {

        if (it == Color.Unspecified) DefaultSeedColor else it
    }

    companion object {
        @Stable
        val Default = ThemeSettings()
    }
}
