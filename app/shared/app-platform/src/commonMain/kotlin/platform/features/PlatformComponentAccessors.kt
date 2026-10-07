package com.wynime.app.platform.features

import com.wynime.app.platform.Context

interface PlatformComponentAccessors {
    val audioManager: AudioManager?
    val brightnessManager: BrightnessManager? get() = null
    val fileRevealer: FileRevealer? get() = null
}

fun Context.getComponentAccessors() = getComponentAccessorsImpl(this)

internal expect fun getComponentAccessorsImpl(context: Context): PlatformComponentAccessors