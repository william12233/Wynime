package com.wynime.app.domain.media.player.prefetch

import com.wynime.app.domain.media.player.ChunkState

data class MediaTimeRange(
    val startMillis: Long,
    val endMillis: Long,
) {
    init {
        require(endMillis >= startMillis) { "endMillis () must be >= startMillis ()" }
    }

    val durationMillis: Long get() = endMillis - startMillis
    val isEmpty: Boolean get() = endMillis == startMillis

    fun overlaps(other: MediaTimeRange): Boolean = startMillis < other.endMillis && other.startMillis < endMillis
}

data class PrefetchSegmentInfo(
    val range: MediaTimeRange,
    val state: ChunkState,
)

data class MediaPrefetchRequest(
    val range: MediaTimeRange,
    val requireBufferedUntilMillis: Long,
)
