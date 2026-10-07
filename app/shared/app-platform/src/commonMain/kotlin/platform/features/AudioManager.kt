package com.wynime.app.platform.features

import com.wynime.app.platform.Context

interface AudioManager {

    fun getVolume(streamType: StreamType): Float

    fun getVolumeStep(streamType: StreamType): Float = 0.01f

    fun setVolume(streamType: StreamType, levelPercentage: Float)
}

enum class StreamType {
    MUSIC,
}
