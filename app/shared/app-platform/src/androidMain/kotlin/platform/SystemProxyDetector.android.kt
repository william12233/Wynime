package com.wynime.app.platform

internal actual fun createSystemProxyDetector(): SystemProxyDetector {
    return NoOpSystemProxyDetector
}