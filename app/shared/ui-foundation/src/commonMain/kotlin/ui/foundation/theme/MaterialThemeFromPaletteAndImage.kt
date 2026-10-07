package com.wynime.app.ui.foundation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.kmpalette.color
import com.kmpalette.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.ui.foundation.resize
import com.wynime.app.ui.foundation.themeColor

@Composable
fun MaterialThemeFromPaletteAndImage(
    palette: Palette?,
    image: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val themeSettings = LocalThemeSettings.current
    val isDark = when (themeSettings.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkThemeDetected()
    }
    val useBlackBackground = themeSettings.useBlackBackground

    var colorScheme by remember { mutableStateOf<ColorScheme?>(null) }

    LaunchedEffect(palette, image) {
        val primaryColor = palette?.vibrantSwatch?.color
            ?: image?.let { withContext(Dispatchers.Default) { it.resize(64, 64).themeColor() } }
            ?: return@LaunchedEffect

        colorScheme = dynamicColorScheme(
            primary = primaryColor,
            isDark = isDark,
            isAmoled = useBlackBackground,
            style = PaletteStyle.TonalSpot,
            modifyColorScheme = { colorScheme ->
                modifyColorSchemeForBlackBackground(
                    colorScheme,
                    isDark,
                    useBlackBackground,
                )
            },
        )
    }

    MaterialTheme(
        colorScheme = colorScheme ?: MaterialTheme.colorScheme,
        content = content,
    )
}
