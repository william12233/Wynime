package tw.wynime.sources.bfzy

import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractPlayerObjectUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
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
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/index.php/vod/search/wd/${urlEncode(variant)}.html",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            val detailPattern = Regex("(?i)/(?:index\\.php/)?vod/detail/id/(\\d+)(?:\\.html)?")
            results += links(page.html).mapNotNull { link ->
                val id = detailPattern.find(link.href)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
                val title = cleanText(link.text).ifBlank { id }
                if (variant.isBlank() || title.contains(variant, ignoreCase = true)) {
                    subject(id, title, absoluteUrl(rootUrl, link.href))
                } else {
                    null
                }
            }.toList()
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/index.php/vod/detail/id/$subjectId.html")
        val title = Regex("(?is)<title>(.*?)</title>")
            .find(page.html)?.groupValues?.getOrNull(1)?.let(::cleanText)
            ?.substringBefore("详情")
            ?.ifBlank { null }
            ?: subjectId
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        val episodePattern = Regex("(?i)/index\\.php/vod/play/id/${Regex.escape(subjectId)}/sid/(\\d+)/nid/(\\d+)(?:\\.html)?")
        links(page.html).forEach { link ->
            val match = episodePattern.find(link.href) ?: return@forEach
            val channelId = match.groupValues[1]
            val episodeId = match.groupValues[2]
            val label = cleanText(link.text).ifBlank { "第${episodeId}集" }
            groups.getOrPut(channelId) { mutableListOf() } += episode(
                id = episodeId,
                title = label,
                pageUrl = absoluteUrl(rootUrl, link.href),
                episodeSort = parseEpisodeNumber(label) ?: episodeId.toFloatOrNull(),
            )
        }
        return SourceSubjectDetails(
            subject = subject(subjectId, title, page.finalUrl),
            channels = groups.map { (channelId, episodes) ->
                SourceChannelEpisodes(
                    SourceChannel(channelId, "bfzym3u8"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/index.php/vod/play/id/${request.subjectId}/sid/${request.channelId}/nid/${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        val playerUrl = parsePlayerUrl(page.html) ?: extractPlayerObjectUrl(page.html)
        resolvedMedia(
            request = request,
            pageUrl = page.finalUrl,
            rawUrl = playerUrl,
            headers = mapOf("Referer" to page.finalUrl),
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        isPlayerPage(url) -> SourceWebResourceMatch.LoadPage
        else -> defaultWebResourceMatch(url)
    }

    private fun isPlayerPage(url: String): Boolean = Regex(
        "(?i)/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html(?:[?#]|$)",
    ).containsMatchIn(url)
}
