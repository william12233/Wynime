package com.wynime.app.videoplayer.player

import kotlin.time.Duration

object PlayerBufferPolicy {
    val forward: Duration get() = platformPlayerBufferDuration
    val backward: Duration get() = platformPlayerBufferDuration

    const val MPV_MAX_BYTES_PER_DIRECTION: Long = 96L * 1024 * 1024
}

internal expect val platformPlayerBufferDuration: Duration
