package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformFontFamily(fontName: String?): PlatformFontFamily {
    return PlatformFontFamily(null)
}