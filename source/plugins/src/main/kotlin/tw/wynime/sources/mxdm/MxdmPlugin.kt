package tw.wynime.sources.mxdm

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

class MxdmEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = MxdmPlugin(context)
}

internal class MxdmPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "mxdm",
    displayName = "MX動漫",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://www.dcc3.com",
    iconUrl = "https://www.dcc3.com/favicon.ico",
    description = "MX動漫公開番劇與分集播放頁來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/search/?wd=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += dynamicSearchLinks(page.html, variant, Regex("(?i)/detail/(\\d+)/"))
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/detail/$subjectId/")
        val title = Regex("(?is)<h2[^>]*>(.*?)</h2>")
            .find(page.html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: page.title.substringBefore("-").ifBlank { subjectId }
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        val episodePattern = Regex("(?i)/play/${Regex.escape(subjectId)}-(\\d+)-(\\d+)(?:[/?#]|$)")
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
        val pageUrl = "$rootUrl/play/${request.subjectId}-${request.channelId}-${request.episodeId}/"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        // The page's inline player URL is region-dependent; let the normal web player
        // execute it so the site can apply its own cookies and fallback logic.
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
        val tabs = Regex(
            "(?is)<div\\b[^>]*class=[\\\"'][^\\\"']*\\btabs\\b[^\\\"']*[\\\"'][^>]*>(.*?)</div>",
        ).find(html)?.groupValues?.getOrNull(1).orEmpty()
        val names = Regex("(?is)<a\\b[^>]*>(.*?)</a>")
            .findAll(tabs)
            .map { cleanText(it.groupValues[1]) }
            .filter { it.isNotBlank() && !it.equals("倒序", ignoreCase = true) }
            .toList()
        val routeIds = links(html)
            .mapNotNull { link ->
                Regex("(?i)/play/${Regex.escape(subjectId)}-(\\d+)-\\d+(?:[/?#]|$)")
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
        "(?i)/play/\\d+-\\d+-\\d+/?(?:[?#]|$)",
    ).containsMatchIn(url)
}
