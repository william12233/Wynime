package com.wynime.app.domain.media.player

import androidx.collection.floatListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class BufferingMediaCacheProgressProvider(
    bufferedPositionMillis: Flow<Long>,
    durationMillis: Flow<Long?>,
) : MediaCacheProgressProvider {
    override val flow: Flow<MediaCacheProgressInfo> =
        combine(bufferedPositionMillis, durationMillis) { buffered, duration ->
            if (buffered < 0 || duration == null || duration <= 0) {
                return@combine -1
            }

            (buffered.toDouble() / duration * RATIO_STEPS).roundToInt().coerceIn(0, RATIO_STEPS)
        }.distinctUntilChanged().map { quantized ->
            if (quantized < 0) MediaCacheProgressInfo.Empty
            else createInfo(quantized.toFloat() / RATIO_STEPS)
        }

    companion object {
        private const val RATIO_STEPS = 1000

        fun createInfo(bufferedRatio: Float): MediaCacheProgressInfo {
            val done = bufferedRatio.coerceIn(0f, 1f)
            return MediaCacheProgressInfo(
                chunkWeights = floatListOf(done, 1f - done),
                chunkStates = listOf(ChunkState.DONE, ChunkState.NONE),
            )
        }
    }
}
