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
import tw.wynime.sources.shared.SourceSiteException

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

    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
        val detailPattern = Regex("(?i)/voddetails-([^/?#]+)\\.html")
        val variants = searchVariants(request.query)
        val searchResults = mutableListOf<SourceSubject>()
        for (variant in variants) {
            val searchPage = try {
                requestPage(
                    "$rootUrl/vodsearch/-------------.html?wd=${urlEncode(variant)}",
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ).html
            } catch (_: SourceSiteException) {
                // E-ACG protects the query endpoint with Cloudflare while its
                // catalogue and playback pages remain available. Continue to
                // the public catalogue fallback instead of failing the source.
                ""
            }
            searchResults += dynamicSearchLinks(searchPage, variant, detailPattern)
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

    private fun searchVariants(query: String): List<String> =
        (searchQueryVariants(query) + simplifyForSearch(query)).distinct()
}
