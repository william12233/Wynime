package tw.wynime.sources.dida

import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.extractPlayerObjectUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.queryMatches
import tw.wynime.sources.shared.searchQueryVariants

class DidaEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = DidaPlugin(context)
}

internal class DidaPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "dida",
    displayName = "嘀嗒影視",
    rootUrl = "https://www.didahd.pro",
    iconUrl = "https://www.didahd.pro/template/mytheme/statics/img/newfavicon.png",
    description = "嘀嗒影視公開番劇與分集來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in didaSearchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/search/-------------.html?wd=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += dynamicSearchLinks(page.html, variant, Regex("(?i)/detail/(\\d+)\\.html"))
                .ifEmpty { aliasSearchLinks(page.html, variant) }
            if (results.any()) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    private fun didaSearchQueryVariants(query: String): List<String> {
        val variants = searchQueryVariants(query).toMutableList()
        val lower = query.lowercase()
        val isReZero = lower.contains("re0") ||
            lower.contains("re:zero") ||
            lower.contains("re：zero") ||
            query.contains("从零开始") ||
            query.contains("從零開始") ||
            query.contains("リゼロ")
        if (isReZero) {
            variants += listOf("异世界生活", "从零开始")
        }
        return variants.distinct()
    }

    private fun aliasSearchLinks(html: String, query: String): List<com.wynime.source.plugin.api.SourceSubject> {
        val detailPattern = Regex("(?i)href=[\\\"']([^\\\"']*/detail/(\\d+)\\.html)[\\\"']")
        return Regex("(?is)<li\\b[^>]*>.*?</li>")
            .findAll(html)
            .mapNotNull { block ->
                val detail = detailPattern.find(block.value) ?: return@mapNotNull null
                if (!queryMatches(cleanText(block.value), query)) return@mapNotNull null
                val title = Regex(
                    "(?is)<h4[^>]*class=[\\\"'][^\\\"']*title[^\\\"']*[\\\"'][^>]*>\\s*<a[^>]*>(.*?)</a>",
                ).find(block.value)?.groupValues?.getOrNull(1)?.let(::cleanText)
                    .orEmpty()
                    .ifBlank {
                        links(block.value)
                            .filter { detailPattern.containsMatchIn(it.href) }
                            .maxByOrNull { cleanText(it.text).length }
                            ?.text
                            ?.let(::cleanText)
                            .orEmpty()
                    }
                    .ifBlank { detail.groupValues[2] }
                subject(
                    id = detail.groupValues[2],
                    title = title,
                    detailUrl = absoluteUrl(rootUrl, detail.groupValues[1]),
                )
            }
            .distinctBy { it.id }
            .take(20)
            .toList()
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/detail/$subjectId.html")
        val title = page.title.substringBefore("-").substringBefore("_").ifBlank { subjectId }
        val subject = subject(subjectId, title, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/play/(\\d+)-(\\d+)-(\\d+)\\.html").find(link.href) ?: return@forEach
            if (match.groupValues[1] != subjectId) return@forEach
            val channelId = match.groupValues[2]
            val episodeId = match.groupValues[3]
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
                    SourceChannel(channelId, channelNamesById(page.html, subjectId)[channelId] ?: "线路$channelId"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    private fun channelNamesById(html: String, subjectId: String): Map<String, String> {
        val namesByPlaylist = Regex(
            "(?is)<li[^>]*>\\s*<a[^>]*href=[\\\"']#playlist(\\d+)[\\\"'][^>]*>(.*?)</a>",
        ).findAll(html).associate { it.groupValues[1] to cleanText(it.groupValues[2]) }
        val channelIdsByPlaylist = Regex(
            "(?is)<div[^>]*id=[\\\"']playlist(\\d+)[\\\"'][^>]*>.*?" +
                "href=[\\\"'](?:[^\\\"']*/)?play/${Regex.escape(subjectId)}-(\\d+)-\\d+\\.html",
        ).findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        return channelIdsByPlaylist.mapNotNull { (playlist, channelId) ->
            namesByPlaylist[playlist]?.takeIf(String::isNotBlank)?.let { channelId to it }
        }.toMap()
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/play/${request.subjectId}-${request.channelId}-${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        val rawPlayerUrl = extractPlayerObjectUrl(page.html)
        val playerUrl = if (extractJsonStringField(page.html, "from")?.equals("BBA", ignoreCase = true) == true) {
            rawPlayerUrl?.let { encodedUrl ->
                absoluteUrl(
                    page.finalUrl,
                    "/static/player/artplayer/?url=${urlEncode(encodedUrl)}",
                )
            }
        } else {
            rawPlayerUrl
        }
        resolvedMedia(
            request = request,
            pageUrl = page.finalUrl,
            rawUrl = playerUrl ?: page.finalUrl,
            headers = mapOf("Referer" to page.finalUrl),
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        url.contains("pan.quark.cn", ignoreCase = true) -> SourceWebResourceMatch.LoadPage
        else -> defaultWebResourceMatch(url)
    }
}
