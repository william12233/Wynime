package tw.wynime.sources.eacg

import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceEpisode
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.decodePlayerUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants

class EacgEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = EacgPlugin(context)
}

internal class EacgPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "eacg",
    displayName = "E-ACG",
    pluginVersion = PLUGIN_VERSION,
    rootUrl = "https://eacg1.com",
    iconUrl = "https://k8dm.com/template/vfed/asset/img/favicon.png",
    description = "E-ACG 動畫搜尋與分集來源",
) {
    private companion object {
        const val XHS_SPECTRUM_PREFIX = "https://sns-video-bd.xhscdn.com/spectrum/"
    }

    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
        val detailPattern = Regex("(?i)/voddetails-([^/?#]+)\\.html")
        val variants = searchQueryVariants(request.query)
        val searchResults = mutableListOf<SourceSubject>()
        for (variant in variants) {
            searchResults += dynamicSearchLinks(
                requestPage(
                    "$rootUrl/vodsearch/-------------.html?wd=${urlEncode(variant)}",
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ).html,
                variant,
                detailPattern,
            )
            if (searchResults.distinctBy { it.id }.size >= request.limit) break
        }
        val uniqueSearchResults = searchResults.distinctBy { it.id }.take(request.limit)
        if (uniqueSearchResults.isNotEmpty()) {
            return uniqueSearchResults
        }

        val fallbackUrls = listOf(
            "$rootUrl/label/front.html",
            "$rootUrl/vodshow/21-----------.html",
            "$rootUrl/vodshow/21--------2---.html",
            "$rootUrl/vodshow/21--------3---.html",
        )
        val fallbackResults = mutableListOf<SourceSubject>()
        for (url in fallbackUrls) {
            val html = requestPage(url, traceId = request.traceId, entryPoint = request.entryPoint).html
            for (variant in variants) {
                fallbackResults += dynamicSearchLinks(html, variant, detailPattern)
            }
            if (fallbackResults.distinctBy { it.id }.size >= request.limit) break
        }
        return fallbackResults.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/voddetails-${subjectId}.html")
        val subject = subject(subjectId, page.title.substringBefore("_").ifBlank { subjectId }, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/Comicplay/([^/-]+)-(\\d+)-(\\d+)\\.html").find(link.href) ?: return@forEach
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
        val names = Regex(
            "(?is)<li[^>]*fed-play-btns[^>]*>\\s*<a[^>]*>(.*?)</a>",
        ).findAll(html).map { cleanText(it.groupValues[1]) }.filter(String::isNotBlank).toList()
        val ids = Regex(
            "(?is)<div[^>]*class=[\\\"'][^\\\"']*fed-play-item[^\\\"']*[\\\"'][^>]*>.*?" +
                "href=[\\\"'](?:[^\\\"']*/)?Comicplay/${Regex.escape(subjectId)}-(\\d+)-\\d+\\.html",
        ).findAll(html).map { it.groupValues[1] }.distinct().toList()
        return ids.zip(names).toMap()
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/Comicplay/${request.subjectId}-${request.channelId}-${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        val playerUrl = parsePlayerUrl(page.html) ?: page.finalUrl
        val decodedPlayerUrl = decodePlayerUrl(playerUrl)
        resolvedMedia(
            request = request,
            pageUrl = page.finalUrl,
            rawUrl = playerUrl,
            referer = if (decodedPlayerUrl?.let(::isEacgDirectMediaUrl) == true) null else page.finalUrl,
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isEacgDirectMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        url.contains("/vip/", ignoreCase = true) -> SourceWebResourceMatch.LoadPage
        else -> SourceWebResourceMatch.Continue
    }

    override fun isDirectMediaUrl(url: String): Boolean =
        super.isDirectMediaUrl(url) || isEacgDirectMediaUrl(url)

    override fun directMediaFormat(url: String): ResolvedMediaFormat =
        if (isEacgDirectMediaUrl(url)) {
            ResolvedMediaFormat.MP4
        } else {
            super.directMediaFormat(url)
        }

    private fun isEacgDirectMediaUrl(url: String): Boolean =
        url.startsWith(XHS_SPECTRUM_PREFIX, ignoreCase = true)
}
