package tw.wynime.sources.girigiri

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
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.SourceSiteException
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonNumberField
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isHttpUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.jsonArrayObjects
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.queryMatches
import tw.wynime.sources.shared.searchQueryVariants

class GirigiriEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = GirigiriPlugin(context)
}

internal class GirigiriPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "girigiri",
    displayName = "Girigiri 愛動漫",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://ani.girigirilove.com",
    iconUrl = "https://ani.girigirilove.com/upload/anime.girigirilove.com_.png",
    description = "Girigiri 愛動漫公開番劇與分集來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
        val results = mutableListOf<SourceSubject>()
        for (variant in searchVariants(request.query)) {
            val apiResults = searchApi(variant, request.traceId, request.entryPoint)
            results += apiResults
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        if (results.isNotEmpty()) {
            return results.distinctBy { it.id }.take(request.limit)
        }

        // Keep a small legacy fallback for mirrors which still expose the old
        // MacCMS pages.  The primary site currently serves a broken SPA shell,
        // so the API above must remain the normal path.
        for (variant in searchVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/search/-------------/?wd=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += dynamicSearchLinks(page.html, variant, Regex("(?i)/(GV[^/?#]+)/?"))
            if (results.isNotEmpty()) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    private suspend fun searchApi(
        query: String,
        traceId: String,
        entryPoint: String,
    ): List<SourceSubject> {
        val page = requestPage(
            "$API_URL?ac=detail&wd=${urlEncode(query)}",
            headers = JSON_HEADERS,
            traceId = traceId,
            entryPoint = entryPoint,
        )
        return jsonArrayObjects(page.html, "list")
            .mapNotNull { item ->
                val id = extractJsonNumberField(item, "vod_id")?.toString()
                    ?: extractJsonStringField(item, "vod_id")?.toIntOrNull()
                    ?: return@mapNotNull null
                val title = extractJsonStringField(item, "vod_name")
                    ?.let(::cleanText)
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                if (!queryMatches(title, query)) return@mapNotNull null
                subject("GV$id", title, "$rootUrl/GV$id/", coverUrl(item))
            }
            .distinctBy { it.id }
            .take(20)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val numericId = subjectId.removePrefix("GV").toIntOrNull()
        if (numericId != null) {
            runCatching {
                parseApiDetail(
                    item = requestApiDetail(subjectId, traceId = "", entryPoint = "SUBJECT_RESOLVE"),
                    subjectId = subjectId,
                )
            }.getOrNull()?.takeIf { it.channels.isNotEmpty() }?.let { return it.toDetails() }
        }
        return parseLegacySubject(subjectId)
    }

    private suspend fun parseLegacySubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/${subjectId.trim('/')}/")
        val title = Regex("(?is)<h1[^>]*>(.*?)</h1>").find(page.html)?.groupValues?.getOrNull(1)
            ?.let(::cleanText)
            ?.ifBlank { null }
            ?: page.title.substringBefore("-").ifBlank { subjectId }
        val subject = subject(subjectId, title, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/play${Regex.escape(subjectId)}-(\\d+)-(\\d+)/?").find(link.href)
                ?: return@forEach
            val channelId = match.groupValues[1]
            val episodeId = match.groupValues[2]
            groups.getOrPut(channelId) { mutableListOf() } += episode(
                id = episodeId,
                title = link.text.ifBlank { "第${episodeId}集" },
                pageUrl = absoluteUrl(rootUrl, link.href),
                episodeSort = parseEpisodeNumber(link.text) ?: episodeId.toFloatOrNull(),
            )
        }
        return SourceSubjectDetails(
            subject = subject,
            channels = groups.map { (channelId, episodes) ->
                SourceChannelEpisodes(
                    SourceChannel(channelId, "线路$channelId"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    override suspend fun resolve(request: SourceResolveRequest): ResolvedMedia {
        val numericId = request.subjectId.removePrefix("GV").toIntOrNull()
        if (numericId != null) {
            runCatching {
                parseApiDetail(
                    item = requestApiDetail(request.subjectId, request.traceId, request.entryPoint),
                    subjectId = request.subjectId,
                )
            }.getOrNull()?.let { detail ->
                detail.channels
                    .firstOrNull { it.channel.id == request.channelId }
                    ?.episodes
                    ?.firstOrNull { it.episode.id == request.episodeId }
                    ?.let { return resolvedDirect(request, detail.subject, it.mediaUrl) }
            }
        }

        val pageUrl = "$rootUrl/play${request.subjectId}-${request.channelId}-${request.episodeId}/"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        return resolvedMedia(
            request,
            page.finalUrl,
            parsePlayerUrl(page.html) ?: tw.wynime.sources.shared.extractPlayerObjectUrl(page.html) ?: page.finalUrl,
        )
    }

    private fun resolvedDirect(
        request: SourceResolveRequest,
        subject: SourceSubject,
        mediaUrl: String,
    ): ResolvedMedia = ResolvedMedia(
        stableIdentity = request.mediaIdentity(metadata.id).asStableId(),
        url = mediaUrl,
        format = mediaFormatFor(mediaUrl),
        headers = MEDIA_HEADERS,
        originalPageUrl = subject.detailUrl,
    )

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        url.contains("moemoekyu", ignoreCase = true) || url.contains("/player", ignoreCase = true) ->
            SourceWebResourceMatch.LoadPage
        else -> SourceWebResourceMatch.Continue
    }

    private suspend fun requestApiDetail(
        subjectId: String,
        traceId: String,
        entryPoint: String,
    ): String {
        val numericId = subjectId.removePrefix("GV")
        val page = requestPage(
            "$API_URL?ac=detail&ids=${urlEncode(numericId)}",
            headers = JSON_HEADERS,
            traceId = traceId,
            entryPoint = entryPoint,
        )
        return jsonArrayObjects(page.html, "list").firstOrNull()
            ?: throw SourceSiteException("Girigiri 沒有回傳 subject 詳情: $subjectId")
    }

    private fun parseApiDetail(item: String, subjectId: String): ApiDetail {
        val title = extractJsonStringField(item, "vod_name")
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: subjectId
        val detailUrl = "$rootUrl/${subjectId.trim('/')}/"
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
        val channels = sourceNames.mapIndexedNotNull { index, rawName ->
            val episodes = parseEpisodes(sourceLists.getOrNull(index).orEmpty(), detailUrl)
            if (episodes.isEmpty()) return@mapIndexedNotNull null
            val channelId = rawName.trim().ifBlank { "line${index + 1}" }
            ApiChannel(
                channel = SourceChannel(channelId, channelDisplayName(rawName, index)),
                episodes = episodes,
            )
        }
        return ApiDetail(subject, channels)
    }

    private fun parseEpisodes(rawSource: String, detailUrl: String): List<SourceEpisodeWithUrl> = rawSource
        .split(EPISODE_SEPARATOR)
        .mapIndexedNotNull { index, rawEpisode ->
            val separator = rawEpisode.indexOf(EPISODE_VALUE_SEPARATOR)
            if (separator <= 0 || separator == rawEpisode.lastIndex) return@mapIndexedNotNull null
            val label = cleanText(rawEpisode.substring(0, separator))
                .ifBlank { "第${index + 1}集" }
            val mediaUrl = rawEpisode.substring(separator + 1).trim()
            if (!isHttpUrl(mediaUrl) || !isMediaUrl(mediaUrl)) return@mapIndexedNotNull null
            SourceEpisodeWithUrl(
                episode(
                    id = label.substringBefore(' ').ifBlank { (index + 1).toString() },
                    title = label,
                    pageUrl = detailUrl,
                    episodeSort = parseEpisodeNumber(label) ?: (index + 1).toFloat(),
                ),
                mediaUrl,
            )
        }

    private fun channelDisplayName(rawName: String, index: Int): String = when (rawName.trim().lowercase()) {
        "chs", "china", "简中", "中文" -> "简中"
        "cht", "繁中" -> "繁中"
        else -> cleanText(rawName).ifBlank { "线路${index + 1}" }
    }

    private fun coverUrl(item: String): String? = extractJsonStringField(item, "vod_pic")
        ?.trim()
        ?.takeIf(::isHttpUrl)

    private fun mediaFormatFor(url: String): ResolvedMediaFormat =
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            ResolvedMediaFormat.HLS
        } else {
            ResolvedMediaFormat.MP4
        }

    private fun searchVariants(query: String): List<String> =
        (searchQueryVariants(query) + simplifyForSearch(query)).distinct()

    private data class ApiDetail(
        val subject: SourceSubject,
        val channels: List<ApiChannel>,
    ) {
        fun toDetails(): SourceSubjectDetails = SourceSubjectDetails(
            subject = subject,
            channels = channels.map { channel ->
                SourceChannelEpisodes(
                    channel = channel.channel,
                    episodes = channel.episodes.map { it.episode },
                )
            },
        )
    }

    private data class ApiChannel(
        val channel: SourceChannel,
        val episodes: List<SourceEpisodeWithUrl>,
    )

    private data class SourceEpisodeWithUrl(
        val episode: SourceEpisode,
        val mediaUrl: String,
    )

    private companion object {
        const val API_URL = "https://m3u8.girigirilove.com/api.php/provide/vod/"
        const val SOURCE_SEPARATOR = "$$"
        const val EPISODE_SEPARATOR = "#"
        const val EPISODE_VALUE_SEPARATOR = '$'
        val JSON_HEADERS = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "https://ani.girigirilove.com/",
        )
        val MEDIA_HEADERS = mapOf("Accept" to "*/*")
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
