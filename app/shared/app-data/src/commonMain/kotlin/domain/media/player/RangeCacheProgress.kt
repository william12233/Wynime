package com.wynime.app.domain.media.player

import androidx.collection.MutableFloatList
import com.wynime.app.domain.media.player.prefetch.PrefetchSegmentInfo

fun buildRangeCacheProgressInfo(
    durationMillis: Long,
    ranges: List<PrefetchSegmentInfo>,
): MediaCacheProgressInfo {
    if (durationMillis <= 0L) return MediaCacheProgressInfo.Empty
    val clamped = ranges.mapNotNull { segment ->
        val start = segment.range.startMillis.coerceIn(0L, durationMillis)
        val end = segment.range.endMillis.coerceIn(0L, durationMillis)
        if (end <= start) null else Triple(start, end, segment.state)
    }
    if (clamped.isEmpty()) {
        return MediaCacheProgressInfo(
            chunkWeights = androidx.collection.floatListOf(1f),
            chunkStates = listOf(ChunkState.NONE),
        )
    }
    val boundaries = buildSet {
        add(0L)
        add(durationMillis)
        for ((start, end, _) in clamped) {
            add(start)
            add(end)
        }
    }.sorted()

    val weights = MutableFloatList(boundaries.size)
    val states = ArrayList<ChunkState>(boundaries.size)
    for (i in 0 until boundaries.lastIndex) {
        val a = boundaries[i]
        val b = boundaries[i + 1]
        var state = ChunkState.NONE
        for ((start, end, s) in clamped) {
            if (start <= a && b <= end && s.priority > state.priority) state = s
        }
        val weight = (b - a).toFloat() / durationMillis
        if (states.isNotEmpty() && states.last() == state) {
            weights[weights.lastIndex] = weights[weights.lastIndex] + weight
        } else {
            weights.add(weight)
            states.add(state)
        }
    }
    return MediaCacheProgressInfo(chunkWeights = weights, chunkStates = states)
}

private val ChunkState.priority: Int
    get() = when (this) {
        ChunkState.NONE -> 0
        ChunkState.NOT_AVAILABLE -> 1
        ChunkState.DOWNLOADING -> 2
        ChunkState.DONE -> 3
    }
