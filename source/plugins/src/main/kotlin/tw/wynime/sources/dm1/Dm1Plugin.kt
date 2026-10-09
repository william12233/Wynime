package tw.wynime.sources.dm1

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
import tw.wynime.sources.shared.extractPlayerObjectUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.normalizePresentationTitle
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants

class Dm1EntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = Dm1Plugin(context)
}

internal class Dm1Plugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "dm1",
    displayName = "稀飯動漫",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://dm1.xfdm.pro",
    iconUrl = "https://dm1.xfdm.pro/upload/site/20240308-1/813e41f81d6f85bfd7a44bf8a813f9e5.png",
    description = "稀飯動漫公開分集來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            results += dynamicSearchLinks(
                requestPage(
                    "$rootUrl/search.html?wd=${urlEncode(variant)}",
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ).html,
                variant,
                Regex("(?i)/bangumi/(\\d+)\\.html"),
            )
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/bangumi/$subjectId.html")
        val title = normalizePresentationTitle(
            page.title.substringBefore("-").substringBefore("_"),
        ).ifBlank { subjectId }
        val subject = subject(subjectId, title, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/watch/(\\d+)/(\\d+)/(\\d+)\\.html").find(link.href) ?: return@forEach
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
                    SourceChannel(channelId, channelNamesById(page.html)[channelId] ?: "线路$channelId"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    private fun channelNamesById(html: String): Map<String, String> {
        val tabs = Regex(
            "(?is)<div[^>]*class=[\\\"'][^\\\"']*anthology-tab[^\\\"']*[\\\"'][^>]*>.*?" +
                "<div[^>]*class=[\\\"'][^\\\"']*swiper-wrapper[^\\\"']*[\\\"'][^>]*>(.*?)</div>",
        ).find(html)?.groupValues?.getOrNull(1).orEmpty()
        val names = Regex("(?is)<a[^>]*class=[\\\"'][^\\\"']*swiper-slide[^\\\"']*[\\\"'][^>]*>(.*?)</a>")
            .findAll(tabs)
            .map { cleanText(it.groupValues[1]) }
            .filter(String::isNotBlank)
            .toList()
        return names.mapIndexed { index, name -> (index + 1).toString() to name }.toMap()
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/watch/${request.subjectId}/${request.channelId}/${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        resolvedMedia(request, page.finalUrl, extractPlayerObjectUrl(page.html) ?: page.finalUrl)
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = defaultWebResourceMatch(url)
}
