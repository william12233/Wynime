package com.wynime.tools.datasourcetestmcp.video

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import com.wynime.app.domain.media.hls.HlsManifestFilter
import com.wynime.app.domain.media.hls.HlsManifestFilterStatus
import com.wynime.utils.httpdownloader.m3u.DefaultM3u8Parser
import com.wynime.utils.httpdownloader.m3u.M3u8Playlist
import com.wynime.utils.ktor.UrlHelpers
import kotlin.math.abs
import kotlin.math.roundToLong

class M3u8AdAnalyzer(
    private val httpClient: HttpClient,
) {

    suspend fun analyze(url: String, headers: Map<String, String>, assumeHls: Boolean = false): AdAnalysisResult {
        val lower = url.lowercase()
        val isM3u8 = lower.contains(".m3u8") || lower.contains("mpegurl")
        if (!isM3u8 && !assumeHls) {
            return AdAnalysisResult(
                suspicion = "unknown",
                reasons = listOf("非 HLS 播放列表, 无法从结构判断广告 (可看首帧截图)"),
            )
        }
        return try {
            analyzePlaylist(url, headers, depth = 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AdAnalysisResult(
                suspicion = "unknown",
                reasons = listOf("播放列表分析失败: ${e::class.simpleName}: ${e.message.orEmpty()}"),
            )
        }
    }

    private suspend fun analyzePlaylist(url: String, headers: Map<String, String>, depth: Int): AdAnalysisResult {
        val text = fetch(url, headers)
        val lines = text.lines().map { it.trim() }

        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") } &&
            lines.none { it.startsWith("#EXTINF") }
        ) {
            if (depth >= 2) {
                return AdAnalysisResult(suspicion = "unknown", reasons = listOf("master playlist 嵌套过深"))
            }
            val variant = lines.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return AdAnalysisResult(suspicion = "unknown", reasons = listOf("master playlist 无可用变体"))
            return analyzePlaylist(UrlHelpers.computeAbsoluteUrl(url, variant), headers, depth + 1)
        }

        return analyzeMediaPlaylist(url = url, text = text, lines = lines)
    }

    private fun analyzeMediaPlaylist(url: String, text: String, lines: List<String>): AdAnalysisResult {
        var discontinuityCount = 0
        val segmentDurations = mutableListOf<Double>()
        val segmentHosts = linkedSetOf<String>()
        var pendingDuration: Double? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") -> {
                    discontinuityCount++
                }

                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line.removePrefix("#EXTINF:")
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull()
                }

                line.isNotEmpty() && !line.startsWith("#") -> {
                    pendingDuration?.let { segmentDurations += it }
                    pendingDuration = null
                    hostOf(UrlHelpers.computeAbsoluteUrl(url, line))?.let { segmentHosts += it }
                }
            }
        }

        val playlistHost = hostOf(url)
        val signals = PlaylistAdSignals(
            segmentCount = segmentDurations.size,
            discontinuityCount = discontinuityCount,
            distinctSegmentHosts = segmentHosts.toList(),
            leadingSegmentDurations = segmentDurations.take(5),
            medianSegmentDuration = segmentDurations.median(),
        )
        val filterErrors = mutableListOf<String>()
        val hlsFilter = runClientFilter(text = text, url = url, errors = filterErrors)
        return score(signals, playlistHost, hlsFilter, filterErrors)
    }

    private fun runClientFilter(text: String, url: String, errors: MutableList<String>): HlsFilterAnalysis? =
        runCatching {
            val result = HlsManifestFilter.filter(text, url)
            val removedGroups = if (result.removedGroups.isEmpty()) {
                emptyList()
            } else {
                val segments = (DefaultM3u8Parser.parse(text, url) as? M3u8Playlist.MediaPlaylist)
                    ?.segments.orEmpty()

                val startOffsets = DoubleArray(segments.size + 1)
                segments.forEachIndexed { i, segment ->
                    startOffsets[i + 1] = startOffsets[i] + segment.duration.toDouble()
                }
                result.removedGroups.map { group ->
                    val start = startOffsets.getOrElse(group.startSegmentIndex) { 0.0 }
                    RemovedAdGroup(
                        reasons = group.reasons,
                        segmentCount = group.segmentCount,
                        durationSeconds = group.duration.round3(),
                        startOffsetSeconds = start.round3(),
                        endOffsetSeconds = (start + group.duration).round3(),
                        startSegmentIndex = group.startSegmentIndex,
                        endSegmentIndex = group.endSegmentIndex,
                        firstSegmentUri = segments.getOrNull(group.startSegmentIndex)?.uri,
                    )
                }
            }
            HlsFilterAnalysis(
                status = result.status.name.lowercase(),
                reason = result.reason,
                filterable = result.status == HlsManifestFilterStatus.Filtered,
                mediaPlaylistUrl = url,
                removedGroups = removedGroups,
            )
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            errors += "Ani HLS 广告过滤器分析失败: ${e::class.simpleName}: ${e.message.orEmpty()}"
            null
        }

    private fun score(
        signals: PlaylistAdSignals,
        playlistHost: String?,
        hlsFilter: HlsFilterAnalysis?,
        extraReasons: List<String>,
    ): AdAnalysisResult {
        val reasons = mutableListOf<String>()
        var score = 0

        if (hlsFilter != null && hlsFilter.filterable) {
            score += 2
            val totalSeconds = hlsFilter.removedGroups.sumOf { it.durationSeconds }
            reasons += "Ani HLS 广告过滤器识别出 ${hlsFilter.removedGroups.size} 组疑似插入广告片段 " +
                    "(共 ${"%.1f".format(totalSeconds)}s, 可自动滤除)"
        }

        if (signals.discontinuityCount > 0) {
            score += 2
            reasons += "播放列表含 ${signals.discontinuityCount} 处 EXT-X-DISCONTINUITY (广告/多段拼接标志)"
        }

        val foreignHosts = signals.distinctSegmentHosts.filter { host ->
            playlistHost != null && !sameSite(host, playlistHost)
        }
        if (signals.distinctSegmentHosts.size > 1) {
            score += 1
            reasons += "分片来自 ${signals.distinctSegmentHosts.size} 个不同主机: ${signals.distinctSegmentHosts.joinToString()}"
        } else if (foreignHosts.isNotEmpty()) {
            score += 1
            reasons += "分片主机 (${foreignHosts.joinToString()}) 与播放列表 ($playlistHost) 不同站点"
        }

        val median = signals.medianSegmentDuration
        if (median != null && median > 0 && signals.leadingSegmentDurations.isNotEmpty()) {
            val lead = signals.leadingSegmentDurations.first()
            if (lead > 0 && abs(lead - median) / median > 0.5 && lead < median) {
                score += 1
                reasons += "首个分片时长 ${lead}s 明显短于分片中位数 ${median}s (疑似前贴片)"
            }
        }

        val suspicion = when {
            score >= 3 -> "suspected_high"
            score == 2 -> "suspected_medium"
            score == 1 -> "suspected_low"
            else -> "none"
        }
        if (suspicion == "none") {
            reasons += "播放列表结构均匀 (无 discontinuity, 单一分片来源), 未见明显广告拼接"
        }
        reasons += extraReasons
        return AdAnalysisResult(suspicion = suspicion, reasons = reasons, playlist = signals, hlsFilter = hlsFilter)
    }

    private suspend fun fetch(url: String, headers: Map<String, String>): String {
        return httpClient.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.bodyAsText()
    }

    private fun hostOf(url: String): String? = runCatching { Url(url).host }.getOrNull()?.lowercase()

    private fun sameSite(a: String, b: String): Boolean {
        fun root(h: String) = h.split('.').takeLast(2).joinToString(".")
        return root(a) == root(b)
    }

    private fun List<Double>.median(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun Double.round3(): Double = (this * 1000).roundToLong() / 1000.0
}
