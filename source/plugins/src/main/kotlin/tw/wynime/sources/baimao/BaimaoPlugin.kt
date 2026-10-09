package tw.wynime.sources.baimao

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
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants

class BaimaoEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = BaimaoPlugin(context)
}

internal class BaimaoPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "baimao",
    displayName = "白猫動漫",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://www.bmmdmm.com",
    iconUrl = "https://www.bmmdmm.com/hdst/hm_pic/favicon.ico",
    description = "白猫動漫公開番劇與分集播放頁來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/s_all?ex=1&kw=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += dynamicSearchLinks(page.html, variant, Regex("(?i)/show/(\\d+)\\.html"))
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/show/$subjectId.html")
        val title = Regex("(?is)<h1[^>]*>(.*?)</h1>")
            .find(page.html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: page.title.substringBefore("—").substringBefore("-").ifBlank { subjectId }
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        val episodePattern = Regex("(?i)/play/${Regex.escape(subjectId)}-(\\d+)-(\\d+)\\.html")
        links(page.html).forEach { link ->
            val match = episodePattern.find(link.href) ?: return@forEach
            val channelId = match.groupValues[1]
            val episodeId = match.groupValues[2]
            groups.getOrPut(channelId) { mutableListOf() } += episode(
                id = episodeId,
                title = link.text.ifBlank { "第${episodeId}集" },
                pageUrl = absoluteUrl(rootUrl, link.href),
                episodeSort = parseEpisodeNumber(link.text) ?: episodeId.toFloatOrNull(),
            )
        }
        val channelNames = channelNamesById(page.html, subjectId)
        return SourceSubjectDetails(
            subject = subject(subjectId, title, page.finalUrl),
            channels = groups.map { (channelId, episodes) ->
                SourceChannelEpisodes(
                    SourceChannel(channelId, channelNames[channelId] ?: "線路$channelId"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/play/${request.subjectId}-${request.channelId}-${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        // The site initialises its player after page load. Returning the normal play page
        // keeps the request in the host WebView instead of depending on a transient URL.
        resolvedMedia(
            request = request,
            pageUrl = page.finalUrl,
            rawUrl = null,
            headers = mapOf("Referer" to page.finalUrl),
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        isPlayerPage(url) -> SourceWebResourceMatch.LoadPage
        else -> defaultWebResourceMatch(url)
    }

    private fun channelNamesById(html: String, subjectId: String): Map<String, String> {
        val menu = Regex(
            "(?is)<ul\\b[^>]*\\bid=[\\\"']menu0[\\\"'][^>]*>(.*?)</ul>",
        ).find(html)?.groupValues?.getOrNull(1).orEmpty()
        val names = Regex("(?is)<li\\b[^>]*>(.*?)</li>")
            .findAll(menu)
            .map { cleanText(it.groupValues[1]) }
            .filter(String::isNotBlank)
            .toList()
        val routeIds = links(html)
            .mapNotNull { link ->
                Regex("(?i)/play/${Regex.escape(subjectId)}-(\\d+)-\\d+\\.html")
                    .find(link.href)
                    ?.groupValues
                    ?.getOrNull(1)
            }
            .distinct()
            .toList()
        return routeIds.mapIndexed { index, channelId ->
            channelId to (names.getOrNull(index) ?: "線路$channelId")
        }.toMap()
    }

    private fun isPlayerPage(url: String): Boolean = Regex(
        "(?i)/play/\\d+-\\d+-\\d+\\.html(?:[?#]|$)",
    ).containsMatchIn(url)
}
