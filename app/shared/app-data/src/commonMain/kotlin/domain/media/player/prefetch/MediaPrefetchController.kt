package com.wynime.app.domain.media.player.prefetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import com.wynime.app.domain.media.hls.HlsPlaybackProxySession
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData

class MediaPrefetchController(
    private val player: MediampPlayer,
    hlsProxySession: Flow<HlsPlaybackProxySession?>,
    scope: CoroutineScope,
) {
    private data class BoundRequest(val request: MediaPrefetchRequest, val media: MediaData?)

    private val request = MutableStateFlow<BoundRequest?>(null)

    fun setPrefetchRequest(request: MediaPrefetchRequest?) {
        this.request.value = request?.let { BoundRequest(it, player.mediaData.value) }
    }

    val prefetchProgress: Flow<List<PrefetchSegmentInfo>> = hlsProxySession.flatMapLatest { session ->
        session?.prefetchProgress ?: flowOf(emptyList())
    }

    private data class Target(
        val media: MediaData?,
        val hlsSession: HlsPlaybackProxySession?,
        val range: MediaTimeRange?,
    )

    init {
        scope.launch {
            val effectiveRange: Flow<Pair<MediaData?, MediaTimeRange?>> =
                combine(player.mediaData, request) { media, bound ->

                    media to bound?.takeIf { it.media === media }?.request
                }.distinctUntilChanged().flatMapLatest { (media, request) ->
                    if (request == null) {
                        flowOf(media to null)
                    } else {
                        bufferedGate(media, request).map { open -> media to request.range.takeIf { open } }
                    }
                }
            combine(
                effectiveRange,
                hlsProxySession,
            ) { (media, range), hls ->
                Target(media, hls, range)
            }.distinctUntilChanged().collect { apply(it) }
        }
    }

    private fun bufferedGate(media: MediaData?, request: MediaPrefetchRequest): Flow<Boolean> {
        val buffered: Flow<Boolean> = when (media) {
            is UriMediaData -> {
                val buffering = player.features[Buffering]

                buffering?.bufferedPositionMillis?.map { it >= request.requireBufferedUntilMillis - BUFFERED_TOLERANCE_MILLIS }
                    ?: flowOf(true)
            }

            else -> flowOf(false)
        }
        return buffered.distinctUntilChanged().transformWhile { open ->
            emit(open)
            !open
        }
    }

    private suspend fun apply(target: Target) {
        when (val media = target.media) {
            is UriMediaData -> target.hlsSession?.setPrefetchRange(target.range)
            else -> {}
        }
    }

    private companion object {

        private const val BUFFERED_TOLERANCE_MILLIS = 1_000L
    }
}
