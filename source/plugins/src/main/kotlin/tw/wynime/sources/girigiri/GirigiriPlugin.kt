package tw.wynime.sources.girigiri

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
import tw.wynime.sources.shared.extractJsonNumberField
import tw.wynime.sources.shared.extractJsonStringField
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
    rootUrl = "https://ani.girigirilove.com",
    iconUrl = "https://ani.girigirilove.com/upload/anime.girigirilove.com_.png",
    description = "Girigiri 愛動漫公開番劇與分集來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val searchResults = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        val variants = searchQueryVariants(request.query)
        for (variant in variants) {
            val apiResults = searchApi(variant, request.traceId, request.entryPoint)
            searchResults += apiResults
            if (apiResults.isNotEmpty()) break

            val variantResults = dynamicSearchLinks(
                requestPage(
                    "$rootUrl/search/-------------/?wd=${urlEncode(variant)}",
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ).html,
                variant,
                Regex("(?i)/(GV[^/?#]+)/?"),
            )
            searchResults += variantResults

            if (variantResults.isNotEmpty()) break
            if (searchResults.distinctBy { it.id }.size >= request.limit) break
        }
        val uniqueSearchResults = searchResults.distinctBy { it.id }.take(request.limit)
        if (uniqueSearchResults.isNotEmpty()) return uniqueSearchResults

        val category = requestPage("$rootUrl/show/2-----------/").html
        val categoryResults = Regex(
            "(?is)<a\\b(?=[^>]*\\bhref=[\"'](/GV[^\"']+)[\"'])(?=[^>]*\\btitle=[\"']([^\"']+)[\"'])[^>]*>",
        )
            .findAll(category)
            .mapNotNull { match ->
                val title = cleanText(match.groupValues[2])
                if (!queryMatches(title, request.query)) return@mapNotNull null
                val id = match.groupValues[1].trim('/').substringBefore('/')
                subject(id, title, absoluteUrl(rootUrl, match.groupValues[1]))
            }
            .distinctBy { it.id }
            .take(request.limit)
            .toList()
        if (categoryResults.isNotEmpty()) return categoryResults

        if (variants.none { it.equals("re0", ignoreCase = true) }) return emptyList()

        val sitemap = requestPage("$rootUrl/rss/baidu.xml").html
        val sitemapResults = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (match in Regex("(?is)<loc>https?://[^<]*/(GV[^/<>]+?)/?</loc>").findAll(sitemap)) {
            val id = match.groupValues[1]
            val detail = requestPage("$rootUrl/$id/")
            val title = Regex("(?is)<h1[^>]*>(.*?)</h1>").find(detail.html)?.groupValues?.getOrNull(1)
                ?.let(::cleanText)
                ?.ifBlank { null }
                ?: detail.title.substringBefore("-").ifBlank { id }
            if (queryMatches(title, request.query)) {
                sitemapResults += subject(id, title, detail.finalUrl)
                if (sitemapResults.distinctBy { it.id }.size >= request.limit) break
            }
        }
        return sitemapResults.distinctBy { it.id }.take(request.limit)
    }

    private suspend fun searchApi(
        query: String,
        traceId: String,
        entryPoint: String,
    ): List<com.wynime.source.plugin.api.SourceSubject> {
        val page = requestPage(
            "$API_URL?ac=detail&wd=${urlEncode(query)}",
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Referer" to "$rootUrl/",
            ),
            traceId = traceId,
            entryPoint = entryPoint,
        )
        return jsonArrayObjects(page.html, "list")
            .mapNotNull { item ->
                val id = extractJsonNumberField(item, "vod_id")?.toString()
                    ?: extractJsonStringField(item, "vod_id")?.toIntOrNull()
                    ?: return@mapNotNull null
                val title = extractJsonStringField(item, "vod_name")?.let(::cleanText)
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                if (!queryMatches(title, query)) return@mapNotNull null
                subject("GV$id", title, "$rootUrl/GV$id/")
            }
            .distinctBy { it.id }
            .take(20)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/${subjectId.trim('/')}/")
        val title = Regex("(?is)<h1[^>]*>(.*?)</h1>").find(page.html)?.groupValues?.getOrNull(1)
            ?.let(::cleanText)
            ?.ifBlank { null }
            ?: page.title.substringBefore("-").ifBlank { subjectId }
        val subject = subject(subjectId, title, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
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
        val pageUrl = "$rootUrl/play${request.subjectId}-${request.channelId}-${request.episodeId}/"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        resolvedMedia(request, page.finalUrl, extractPlayerObjectUrl(page.html) ?: page.finalUrl)
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        url.contains("moemoekyu", ignoreCase = true) || url.contains("/player", ignoreCase = true) ->
            SourceWebResourceMatch.LoadPage
        else -> SourceWebResourceMatch.Continue
    }

    private companion object {
        const val API_URL = "https://m3u8.girigirilove.com/api.php/provide/vod/"
    }
}
