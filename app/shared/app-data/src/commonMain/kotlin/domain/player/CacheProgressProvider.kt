package com.wynime.app.domain.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import com.wynime.app.domain.media.player.BufferingMediaCacheProgressProvider
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import com.wynime.app.domain.media.player.buildRangeCacheProgressInfo
import com.wynime.app.domain.media.player.prefetch.MediaPrefetchController
import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import com.wynime.app.domain.media.player.prefetch.PrefetchSegmentInfo
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.source.UriMediaData
import kotlin.math.roundToInt

class CacheProgressProvider(
    playerState: MediampPlayer,
    flowScope: CoroutineScope,

    prefetchProgress: Flow<List<PrefetchSegmentInfo>> = flowOf(emptyList()),
) {
    @OptIn(ExperimentalMediampApi::class)
    val cacheProgressInfoFlow = playerState.mediaData
        .flatMapLatest { data ->
            when (data) {
                is UriMediaData -> {
                    val buffering = playerState.features[Buffering]
                    val durationMillis = playerState.mediaProperties.map { it?.durationMillis }
                    if (buffering == null) {
                        prefetchOnlyProgress(prefetchProgress, durationMillis)
                    } else {
                        prefetchProgress.flatMapLatest { prefetch ->
                            if (prefetch.isEmpty()) {
                                BufferingMediaCacheProgressProvider(
                                    bufferedPositionMillis = buffering.bufferedPositionMillis,
                                    durationMillis = durationMillis,
                                ).flow
                            } else {
                                combine(buffering.bufferedPositionMillis, durationMillis) { buffered, duration ->
                                    mergeBufferedAndPrefetched(buffered, duration, prefetch)
                                }.distinctUntilChanged()
                            }
                        }
                    }
                }

                else -> flowOf(MediaCacheProgressInfo.Empty)
            }
        }.shareIn(
            flowScope,
            SharingStarted.WhileSubscribed(),
            replay = 1,
        )
}

private fun prefetchOnlyProgress(
    prefetchProgress: Flow<List<PrefetchSegmentInfo>>,
    durationMillis: Flow<Long?>,
): Flow<MediaCacheProgressInfo> = combine(prefetchProgress, durationMillis) { prefetch, duration ->
    if (prefetch.isEmpty() || duration == null || duration <= 0) {
        MediaCacheProgressInfo.Empty
    } else {
        buildRangeCacheProgressInfo(duration, prefetch)
    }
}.distinctUntilChanged()

internal fun mergeBufferedAndPrefetched(
    bufferedPositionMillis: Long,
    durationMillis: Long?,
    prefetch: List<PrefetchSegmentInfo>,
): MediaCacheProgressInfo {
    if (durationMillis == null || durationMillis <= 0) return MediaCacheProgressInfo.Empty
    val ranges = ArrayList<PrefetchSegmentInfo>(prefetch.size + 1)
    if (bufferedPositionMillis > 0) {

        val quantized = (bufferedPositionMillis.toDouble() / durationMillis * BUFFERED_RATIO_STEPS).roundToInt()
            .coerceIn(0, BUFFERED_RATIO_STEPS)
        val quantizedMillis = durationMillis * quantized / BUFFERED_RATIO_STEPS
        ranges += PrefetchSegmentInfo(MediaTimeRange(0, quantizedMillis), ChunkState.DONE)
    }
    ranges += prefetch
    return buildRangeCacheProgressInfo(durationMillis, ranges)
}

private const val BUFFERED_RATIO_STEPS = 1000
