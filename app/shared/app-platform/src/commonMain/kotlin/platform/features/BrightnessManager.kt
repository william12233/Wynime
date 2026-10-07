package com.wynime.app.platform.features

interface BrightnessManager {

    fun getBrightness(): Float

    fun setBrightness(level: Float)
}