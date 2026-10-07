package com.wynime.app.ui.foundation

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily

val LocalPlatformFontFamily = compositionLocalOf<PlatformFontFamily> {
    error("No PlatformFontFamily provided")
}

class PlatformFontFamily(

    val defaultFontFamily: FontFamily?
)

@Composable
expect fun rememberPlatformFontFamily(
    fontName: String?,
): PlatformFontFamily

@Composable
fun Typography.copyWithPlatformFontFamily(
    platformFontFamily: PlatformFontFamily,
): Typography {
    if (platformFontFamily.defaultFontFamily == null) {
        return this
    }
    return copy(
        bodyLarge = bodyLarge.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        bodyMedium = bodyMedium.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        bodySmall = bodySmall.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        titleLarge = titleLarge.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        titleMedium = titleMedium.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        titleSmall = titleSmall.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        labelLarge = labelLarge.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        labelMedium = labelMedium.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        labelSmall = labelSmall.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        displayLarge = displayLarge.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        displayMedium = displayMedium.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        displaySmall = displaySmall.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        headlineLarge = headlineLarge.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        headlineMedium = headlineMedium.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
        headlineSmall = headlineSmall.copy(
            fontFamily = platformFontFamily.defaultFontFamily,
        ),
    )
}