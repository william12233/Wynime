package com.wynime.app.platform

import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatformDesktop

internal actual fun createSystemProxyDetector(): SystemProxyDetector {
    return when (currentPlatformDesktop()) {
        is Platform.Windows -> WindowsSystemProxyDetector()
        else -> NoOpSystemProxyDetector
    }
}