package com.wynime.app.domain.media.hls

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import com.wynime.app.domain.media.player.prefetch.PrefetchSegmentInfo
import org.openani.mediamp.source.UriMediaData

data class HlsPlaybackOptions(

    val filterSegments: Boolean = false,

    val proxySegments: Boolean = false,

    /**
     * Downloads the selected playback range into the proxy cache. Segment
     * proxying itself is independent from this because some media servers
     * require the proxy even when OP/ED skipping is disabled.
     */
    val prefetchSegments: Boolean = proxySegments,
) {
    val isEnabled: Boolean get() = filterSegments || proxySegments

    companion object {
        val Disabled = HlsPlaybackOptions()
    }
}

interface HlsPlaybackPreparer {
    suspend fun prepare(data: UriMediaData, options: HlsPlaybackOptions): HlsPlaybackPreparerResult
}

data class HlsPlaybackPreparerResult(
    val data: UriMediaData,
    val session: HlsPlaybackProxySession? = null,
)

interface HlsPlaybackProxySession : AutoCloseable {

    fun setPrefetchRange(range: MediaTimeRange?) {}

    val prefetchProgress: Flow<List<PrefetchSegmentInfo>> get() = flowOf(emptyList())
}

object NoopHlsPlaybackPreparer : HlsPlaybackPreparer {
    override suspend fun prepare(data: UriMediaData, options: HlsPlaybackOptions): HlsPlaybackPreparerResult {
        return HlsPlaybackPreparerResult(data)
    }
}
