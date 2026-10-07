package com.wynime.app.domain.media.player

import androidx.collection.FloatList
import androidx.collection.floatListOf
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MediaCacheProgressProvider {
    val flow: Flow<MediaCacheProgressInfo>
}

@Immutable
data class MediaCacheProgressInfo(

    val chunkWeights: FloatList,

    val chunkStates: List<ChunkState>,
) {
    init {
        require(chunkWeights.size == chunkStates.size) {
            "chunkWeights.size (${chunkWeights.size}) != chunkStates.size (${chunkStates.size})"
        }
    }

    companion object {
        val Empty = MediaCacheProgressInfo(
            chunkWeights = floatListOf(),
            chunkStates = listOf(),
        )
    }

    val size = chunkWeights.size
    val lastIndex get() = chunkWeights.size - 1
    fun isEmpty(): Boolean = chunkWeights.isEmpty()
}

enum class ChunkState {

    NONE,

    DOWNLOADING,

    DONE,

    NOT_AVAILABLE
}

private val StaticMediaCacheProgressStateNone = StaticMediaCacheProgressProvider(ChunkState.NONE)
private val StaticMediaCacheProgressStateDone = StaticMediaCacheProgressProvider(ChunkState.DONE)

fun staticMediaCacheProgressState(
    chunkState: ChunkState
): MediaCacheProgressProvider {
    if (chunkState == ChunkState.NONE) return StaticMediaCacheProgressStateNone
    if (chunkState == ChunkState.DONE) return StaticMediaCacheProgressStateDone
    return StaticMediaCacheProgressProvider(chunkState)
}

private class StaticMediaCacheProgressProvider(chunkState: ChunkState) : MediaCacheProgressProvider {
    override val flow: Flow<MediaCacheProgressInfo> = flowOf(
        MediaCacheProgressInfo(
            chunkWeights = floatListOf(1f),
            chunkStates = listOf(chunkState),
        ),
    )
}
