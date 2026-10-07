package com.wynime.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.wynime.app.platform.window.WindowUtils

@Composable
actual fun ScreenOnEffectImpl() {
    DisposableEffect(true) {
        WindowUtils.instance.setPreventScreenSaver(true)
        onDispose {
            WindowUtils.instance.setPreventScreenSaver(false)
        }
    }
}
