package com.wynime.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.wynime.app.platform.window.LocalTitleBarThemeController

@Composable
actual fun OverrideCaptionButtonAppearance(isDark: Boolean) {
    val titleBarController = LocalTitleBarThemeController.current ?: return
    val owner = remember { Any() }
    DisposableEffect(titleBarController, owner, isDark) {
        titleBarController.requestTheme(owner = owner, isDark = isDark)
        onDispose {
            titleBarController.removeTheme(owner = owner)
        }
    }
}