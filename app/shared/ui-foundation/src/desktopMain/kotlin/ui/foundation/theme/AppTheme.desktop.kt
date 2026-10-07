package com.wynime.app.ui.foundation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.utils.platform.isWindows

@Composable
actual fun appColorScheme(
    seedColor: Color,
    useDynamicTheme: Boolean,
    useBlackBackground: Boolean,
    isDark: Boolean,
): ColorScheme {
    val actualSeedColor = if (useDynamicTheme && isPlatformSupportDynamicTheme()) {
        val currentWindowColor by LocalPlatformWindow.current.accentColor.collectAsState(Color.Unspecified)

        currentWindowColor.takeOrElse { seedColor }
    } else {
        seedColor
    }

    return remember(actualSeedColor, isDark, useBlackBackground) {
        dynamicColorScheme(
            primary = actualSeedColor,
            isDark = isDark,
            isAmoled = useBlackBackground,
            style = PaletteStyle.TonalSpot,
            modifyColorScheme = { colorScheme ->
                modifyColorSchemeForBlackBackground(colorScheme, isDark, useBlackBackground)
            },
        )
    }
}

@Composable
actual fun isPlatformSupportDynamicTheme(): Boolean {
    return LocalPlatform.current.run { isWindows() }
}
