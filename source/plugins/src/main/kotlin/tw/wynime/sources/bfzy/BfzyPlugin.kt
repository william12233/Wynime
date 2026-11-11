package tw.wynime.sources.bfzy

import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourceEpisode
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.source.plugin.api.mediaIdentity
import java.net.URI
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.SourceSiteException
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonNumberField
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isHttpUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.jsonArrayObjects
import tw.wynime.sources.shared.mediaFormat
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.queryMatches
import tw.wynime.sources.shared.searchQueryVariants

class BfzyEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = BfzyPlugin(context)
}

internal class BfzyPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "bfzy",
    displayName = "暴風資源",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://bfzy.tv",
    iconUrl = "https://bfzy.tv/favicon.ico",
    description = "暴風資源公開影片與 bfzym3u8 播放來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
        val results = mutableListOf<SourceSubject>()
        for (variant in searchVariants(request.query)) {
            val page = requestPage(
                "$API_URL?ac=detail&wd=${urlEncode(variant)}",
                headers = JSON_HEADERS,
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += jsonArrayObjects(page.html, "list")
                .mapNotNull { parseSubject(it, variant) }
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit.coerceAtLeast(0))
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        return parseDetail(
            item = requestDetail(subjectId, traceId = "", entryPoint = "SUBJECT_RESOLVE"),
            subjectId = subjectId,
        ).toDetails()
    }

    override suspend fun resolve(request: SourceResolveRequest): ResolvedMedia {
        val detail = parseDetail(
            item = requestDetail(
                subjectId = request.subjectId,
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            ),
            subjectId = request.subjectId,
        )
        val episode = detail.channels
            .firstOrNull { it.channel.id == request.channelId }
            ?.episodes
            ?.firstOrNull { it.episode.id == request.episodeId }
            ?: throw SourceSiteException(
                "暴風資源找不到可播放的集數: ${request.subjectId}/${request.channelId}/${request.episodeId}",
            )

        return ResolvedMedia(
            stableIdentity = request.mediaIdentity(metadata.id).asStableId(),
            url = episode.mediaUrl,
            format = ResolvedMediaFormat.HLS,
            headers = MEDIA_HEADERS,
            originalPageUrl = detail.subject.detailUrl,
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        else -> SourceWebResourceMatch.Continue
    }

    private suspend fun requestDetail(
        subjectId: String,
        traceId: String,
        entryPoint: String,
    ): String {
        val page = requestPage(
            "$API_URL?ac=detail&ids=${urlEncode(subjectId)}",
            headers = JSON_HEADERS,
            traceId = traceId,
            entryPoint = entryPoint,
        )
        return jsonArrayObjects(page.html, "list").firstOrNull()
            ?: throw SourceSiteException("暴風資源沒有回傳 subject 詳情: $subjectId")
    }

    private fun parseSubject(item: String, query: String): SourceSubject? {
        val id = extractJsonNumberField(item, "vod_id")?.toString()
            ?: extractJsonStringField(item, "vod_id")?.trim()?.takeIf(String::isNotBlank)
            ?: return null
        val title = extractJsonStringField(item, "vod_name")
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (!queryMatches(title, query)) return null
        return subject(id, title, detailUrl(id), coverUrl(item))
    }

    private fun parseDetail(item: String, subjectId: String): ParsedDetail {
        val title = extractJsonStringField(item, "vod_name")
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: subjectId
        val detailUrl = detailUrl(subjectId)
        val subject = subject(
            id = subjectId,
            title = title,
            detailUrl = detailUrl,
            coverUrl = coverUrl(item),
        )
        val sourceNames = extractJsonStringField(item, "vod_play_from")
            .orEmpty()
            .split(SOURCE_SEPARATOR)
        val sourceLists = extractJsonStringField(item, "vod_play_url")
            .orEmpty()
            .split(SOURCE_SEPARATOR)
        val sourceIndex = sourceNames.indexOfFirst { it.trim().equals(CHANNEL_ID, ignoreCase = true) }
        val episodes = if (sourceIndex >= 0) {
            parseEpisodes(sourceLists.getOrNull(sourceIndex).orEmpty(), detailUrl)
        } else {
            emptyList()
        }
        return ParsedDetail(
            subject = subject,
            channels = if (episodes.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ParsedChannel(
                        channel = SourceChannel(CHANNEL_ID, CHANNEL_ID),
                        episodes = episodes.mapIndexed { index, parsedEpisode ->
                            val episodeSort = parsedEpisode.episode.episodeSort
                                ?: (index + 1).toFloat()
                            parsedEpisode.copy(
                                episode = episode(
                                    id = "$CHANNEL_ID-${index + 1}",
                                    title = parsedEpisode.episode.displayName,
                                    pageUrl = detailUrl,
                                    episodeSort = episodeSort,
                                ),
                            )
                        },
                    ),
                )
            },
        )
    }

    private fun parseEpisodes(rawSource: String, detailUrl: String): List<ParsedEpisode> = rawSource
        .split(EPISODE_SEPARATOR)
        .mapNotNull { rawEpisode ->
            val separator = rawEpisode.indexOf(EPISODE_VALUE_SEPARATOR)
            if (separator <= 0 || separator == rawEpisode.lastIndex) return@mapNotNull null
            val title = cleanText(rawEpisode.substring(0, separator))
                .ifBlank { return@mapNotNull null }
            val mediaUrl = rawEpisode.substring(separator + 1).trim()
            if (!isDirectHttpsHls(mediaUrl)) return@mapNotNull null
            ParsedEpisode(
                episode = episode(
                    id = "pending",
                    title = title,
                    pageUrl = detailUrl,
                    episodeSort = parseEpisodeNumber(title),
                ),
                mediaUrl = mediaUrl,
            )
        }

    private fun coverUrl(item: String): String? = extractJsonStringField(item, "vod_pic")
        ?.trim()
        ?.takeIf(::isHttpUrl)

    private fun isDirectHttpsHls(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            isMediaUrl(url) &&
            mediaFormat(url) == ResolvedMediaFormat.HLS
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun detailUrl(subjectId: String): String =
        "$rootUrl/index.php/vod/detail/id/$subjectId.html"

    private fun searchVariants(query: String): List<String> =
        (searchQueryVariants(query) + simplifyForSearch(query)).distinct()

    private data class ParsedDetail(
        val subject: SourceSubject,
        val channels: List<ParsedChannel>,
    ) {
        fun toDetails(): SourceSubjectDetails = SourceSubjectDetails(
            subject = subject,
            channels = channels.map { parsedChannel ->
                SourceChannelEpisodes(
                    channel = parsedChannel.channel,
                    episodes = parsedChannel.episodes.map { it.episode },
                )
            },
        )
    }

    private data class ParsedChannel(
        val channel: SourceChannel,
        val episodes: List<ParsedEpisode>,
    )

    private data class ParsedEpisode(
        val episode: SourceEpisode,
        val mediaUrl: String,
    )

    private companion object {
        const val API_URL = "https://bfzyapi.com/api.php/provide/vod"
        const val CHANNEL_ID = "bfzym3u8"
        const val SOURCE_SEPARATOR = "$$"
        const val EPISODE_SEPARATOR = "#"
        const val EPISODE_VALUE_SEPARATOR = '$'
        val JSON_HEADERS = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "https://bfzy.tv/",
        )
        val MEDIA_HEADERS = mapOf(
            "Accept" to "*/*",
            "Referer" to "https://bfzy.tv/",
        )
        val TRADITIONAL_TO_SIMPLIFIED = mapOf(
            '術' to '术', '迴' to '回', '戰' to '战', '網' to '网', '動' to '动',
            '畫' to '画', '國' to '国', '學' to '学', '會' to '会', '語' to '语',
            '開' to '开', '發' to '发', '現' to '现', '線' to '线', '號' to '号',
            '劇' to '剧', '戲' to '戏', '經' to '经', '傳' to '传', '這' to '这',
            '與' to '与', '來' to '来', '點' to '点', '後' to '后', '處' to '处',
            '過' to '过', '還' to '还', '對' to '对', '進' to '进', '從' to '从',
            '異' to '异', '無' to '无', '萬' to '万', '個' to '个', '種' to '种',
            '電' to '电', '優' to '优', '質' to '质', '廣' to '广', '劍' to '剑',
            '龍' to '龙', '風' to '风', '冊' to '册', '標' to '标',
        )

        fun simplifyForSearch(value: String): String = buildString(value.length) {
            value.forEach { append(TRADITIONAL_TO_SIMPLIFIED[it] ?: it) }
        }
    }
}
