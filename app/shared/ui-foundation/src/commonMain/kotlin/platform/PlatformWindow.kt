package com.wynime.app.platform

expect class PlatformWindow {

    val isExactlyMaximized: Boolean

    val isUndecoratedFullscreen: Boolean
    val deviceOrientation: DeviceOrientation

    fun maximize()
    fun floating()

    val isAlwaysOnTop: Boolean

    fun setAlwaysOnTop(alwaysOnTop: Boolean)
}

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
}