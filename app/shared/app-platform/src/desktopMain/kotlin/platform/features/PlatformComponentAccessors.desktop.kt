package com.wynime.app.platform.features

import com.wynime.app.platform.Context

actual fun getComponentAccessorsImpl(context: Context): PlatformComponentAccessors = DesktopPlatformComponentAccessors()

private class DesktopPlatformComponentAccessors : PlatformComponentAccessors {
    override val audioManager: AudioManager?
        get() = null
    override val fileRevealer: FileRevealer
        get() = DesktopFileRevealer
}
