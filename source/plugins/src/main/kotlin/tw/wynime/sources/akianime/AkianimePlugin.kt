package tw.wynime.sources.akianime

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

class AkianimeEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = AkianimePlugin(context)
}

internal class AkianimePlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "akianime",
    displayName = "AkiAnime",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://www.akianime.cc",
    iconUrl = "https://www.akianime.cc/favicon.ico",
    description = "AkiAnime 公開番劇與分集播放頁來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/bgmsearch/-------------.html?wd=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += dynamicSearchLinks(page.html, variant, Regex("(?i)/bgmdetail/([^/?#]+)\\.html"))
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/bgmdetail/$subjectId.html")
        val title = Regex("(?is)<h3[^>]*class=[\\\"'][^\\\"']*slide-info-title[^\\\"']*[\\\"'][^>]*>(.*?)</h3>")
            .find(page.html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: page.title.substringBefore("-").ifBlank { subjectId }
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        val episodePattern = Regex("(?i)/bgmplay/${Regex.escape(subjectId)}-(\\d+)-(\\d+)\\.html")
        resourceEpisodeLinks(page.html).forEach { link ->
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
        val pageUrl = "$rootUrl/bgmplay/${request.subjectId}-${request.channelId}-${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        // Aki exposes a usable HLS URL in player_aaaa for some lines. Encrypted or
        // otherwise non-HTTP values still fall back to the normal site player page.
        resolvedMedia(
            request = request,
            pageUrl = page.finalUrl,
            rawUrl = extractPlayerObjectUrl(page.html),
            headers = mapOf("Referer" to page.finalUrl),
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        isPlayerPage(url) -> SourceWebResourceMatch.LoadPage
        else -> defaultWebResourceMatch(url)
    }

    private fun channelNamesById(html: String, subjectId: String): Map<String, String> {
        val names = Regex(
            "(?is)<a\\b[^>]*class=[\\\"'][^\\\"']*swiper-slide[^\\\"']*[\\\"'][^>]*>(.*?)</a>",
        ).findAll(html)
            .map {
                cleanText(it.groupValues[1])
                    .replace(Regex("(?i)^播放\\s*"), "")
                    .replace(Regex("\\s+\\d+$"), "")
                    .trim()
            }
            .filter(String::isNotBlank)
            .toList()
        val routeIds = resourceEpisodeLinks(html)
            .mapNotNull { link ->
                Regex("(?i)/bgmplay/${Regex.escape(subjectId)}-(\\d+)-\\d+\\.html")
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

    private fun resourceEpisodeLinks(html: String) = links(html).filter { link ->
        Regex("(?i)\\bthis-link\\b").containsMatchIn(link.attributes)
    }

    private fun isPlayerPage(url: String): Boolean = Regex(
        "(?i)/bgmplay/[^/?#]+-\\d+-\\d+\\.html(?:[?#]|$)",
    ).containsMatchIn(url)
}
