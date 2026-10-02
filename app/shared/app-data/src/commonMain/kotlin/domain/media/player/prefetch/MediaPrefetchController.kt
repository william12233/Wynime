/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.prefetch

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
import me.him188.ani.app.domain.media.hls.HlsPlaybackProxySession
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData

/**
 * 把 "提前缓存某段时间的媒体" 的请求分发到当前数据源:
 *
 * - HLS (经本地代理播放的 [UriMediaData]): 交给 [HlsPlaybackProxySession] 提前下载对应分片, 进度由 [prefetchProgress] 上报.
 * - 其他数据源: 忽略.
 *
 * ### 不与正常播放抢带宽
 *
 * 预缓存只有在 [MediaPrefetchRequest.requireBufferedUntilMillis] 之前的内容都缓冲好之后才生效.
 * 一旦生效就保持, 直到请求被替换或取消.
 *
 * 请求与调用 [setPrefetchRequest] 时正在播放的媒体绑定, 切换媒体后旧请求自动失效, 不会误作用到新媒体上.
 */
class MediaPrefetchController(
    private val player: MediampPlayer,
    hlsProxySession: Flow<HlsPlaybackProxySession?>,
    scope: CoroutineScope,
) {
    private data class BoundRequest(val request: MediaPrefetchRequest, val media: MediaData?)

    private val request = MutableStateFlow<BoundRequest?>(null)

    /**
     * 设置预缓存请求, 替换之前的请求. 传 `null` 取消.
     */
    fun setPrefetchRequest(request: MediaPrefetchRequest?) {
        this.request.value = request?.let { BoundRequest(it, player.mediaData.value) }
    }

    /** 需要额外显示在进度条上的预缓存进度. */
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
                    // 请求只对发出它时的媒体有效
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

    /**
     * [MediaPrefetchRequest.requireBufferedUntilMillis] 之前的内容是否已缓冲好. 先发出 `false`, 满足后发出 `true` 并结束 (之后不再关闭).
     */
    private fun bufferedGate(media: MediaData?, request: MediaPrefetchRequest): Flow<Boolean> {
        val buffered: Flow<Boolean> = when (media) {
            is UriMediaData -> {
                val buffering = player.features[Buffering]
                // 播放器不上报缓冲位置时无从判断, 直接放行
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
        /** 已缓冲位置与目标位置之间允许的误差, 播放器上报的缓冲位置通常按分片或关键帧对齐. */
        private const val BUFFERED_TOLERANCE_MILLIS = 1_000L
    }
}
