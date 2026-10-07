package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.FontLoadResult

@OptIn(ExperimentalTextApi::class)
@Composable
actual fun rememberPlatformFontFamily(
    fontName: String?,
): PlatformFontFamily {
    if (fontName == null) return PlatformFontFamily(null)

    var resolvedFontFamily by remember { mutableStateOf<FontFamily?>(null) }
    val fontFamilyResolver = LocalFontFamilyResolver.current

    LaunchedEffect(fontFamilyResolver) {
        val fontFamily = FontFamily(fontName)
        resolvedFontFamily = runCatching {
            val result = fontFamilyResolver.resolve(fontFamily).value as FontLoadResult
            if (result.typeface == null || result.typeface?.familyName != fontName) {
                null
            } else {
                fontFamily
            }
        }.getOrNull()
    }

    return PlatformFontFamily(resolvedFontFamily)
}