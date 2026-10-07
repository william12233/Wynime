package com.wynime.app.ui.foundation.layout

import com.wynime.app.platform.Context
import com.wynime.app.platform.PlatformWindow
import com.wynime.app.platform.checkIsDesktop
import com.wynime.app.platform.window.WindowUtils

actual suspend fun Context.setRequestFullScreen(window: PlatformWindow, fullscreen: Boolean) {
    checkIsDesktop()

    if (fullscreen) {
        WindowUtils.instance.setUndecoratedFullscreen(window, windowState, true)
    } else {
        WindowUtils.instance.setUndecoratedFullscreen(window, windowState, false)
    }
}

actual fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean) {
}