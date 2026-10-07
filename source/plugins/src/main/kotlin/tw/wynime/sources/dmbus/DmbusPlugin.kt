package tw.wynime.sources.dmbus

import java.net.URI
import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourceMediaRequestContext
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.source.plugin.api.mediaIdentity
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonNumberField
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.extractPlayerObjectUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants

class DmbusEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = DmbusPlugin(context)
}

internal class DmbusPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "dmbus",
    displayName = "動漫巴士",
    rootUrl = "https://dmbus.cc",
    iconUrl = "https://dmbus.cc/favicon.ico",
    description = "動漫巴士公開番劇與外部播放頁來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<com.wynime.source.plugin.api.SourceSubject> {
        val results = mutableListOf<com.wynime.source.plugin.api.SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/s----------.html?wd=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            results += dynamicSearchLinks(
                page.html,
                "",
                Regex("(?i)/v/(\\d+)\\.html"),
            )
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/v/$subjectId.html")
        val title = Regex("(?is)<h1[^>]*>(.*?)</h1>").find(page.html)?.groupValues?.getOrNull(1)
            ?.let(::cleanText)
            ?.ifBlank { null }
            ?: page.title.substringBefore("-").ifBlank { subjectId }
        val subject = subject(subjectId, title, page.finalUrl)
        val groups = linkedMapOf<String, MutableList<com.wynime.source.plugin.api.SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/p/(\\d+)-(\\d+)-(\\d+)\\.html").find(link.href) ?: return@forEach
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
            "(?is)<ul[^>]*class=[\\\"'][^\\\"']*play_from[^\\\"']*[\\\"'][^>]*>(.*?)</ul>",
        ).find(html)?.groupValues?.getOrNull(1).orEmpty()
        val names = Regex("(?is)<li[^>]*>(.*?)</li>")
            .findAll(tabs)
            .map { cleanText(it.groupValues[1]) }
            .filter(String::isNotBlank)
            .toList()
        return names.mapIndexed { index, name -> (index + 1).toString() to name }.toMap()
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/p/${request.subjectId}-${request.channelId}-${request.episodeId}.html"
        val page = requestPage(pageUrl, traceId = request.traceId, entryPoint = request.entryPoint)
        val iframe = Regex("(?is)<iframe[^>]+src=[\"']([^\"']+)[\"']").find(page.html)?.groupValues?.getOrNull(1)
        val iframeUrl = iframe?.let { absoluteUrl(page.finalUrl, it) }
        val directUrlAndReferer = iframeUrl?.let { url ->
            resolveHhjxPlayer(url)?.let { directUrl -> directUrl to url }
        }
        val directUrl = directUrlAndReferer?.first
        val directReferer = directUrlAndReferer?.second
        if (directUrl == null && iframeUrl != null) {
            ResolvedMedia(
                stableIdentity = request.mediaIdentity(metadata.id).asStableId(),
                url = iframeUrl,
                format = ResolvedMediaFormat.WEB,
                originalPageUrl = page.finalUrl,
            )
        } else {
            val resolved = resolvedMedia(
                request,
                page.finalUrl,
                directUrl ?: extractPlayerObjectUrl(page.html) ?: page.finalUrl,
                headers = if (directReferer != null) {
                    mapOf("Referer" to directReferer)
                } else {
                    emptyMap()
                },
            )
            if (directReferer != null) {

                resolved.copy(
                    headers = emptyMap(),
                    requestContext = SourceMediaRequestContext(),
                )
            } else {
                resolved
            }
        }
    }

    private suspend fun resolveHhjxPlayer(iframeUrl: String): String? {
        if (!iframeUrl.contains("hhjx.hhplayer.com", ignoreCase = true)) return null
        val playerPage = requestPage(iframeUrl)
        val bootstrap = Regex(
            "(?s)window\\.__HHJX_BOOTSTRAP__\\s*=\\s*(\\{.*?\\})\\s*;",
        ).find(playerPage.html)?.groupValues?.getOrNull(1) ?: return null
        val url = extractJsonStringField(bootstrap, "url") ?: return null
        val timestamp = extractJsonNumberField(bootstrap, "t") ?: return null
        val key = extractJsonStringField(bootstrap, "key") ?: return null
        val body = "{\"url\":\"$url\",\"t\":$timestamp,\"key\":\"$key\",\"client_fallback\":false}"
        val response = requestJson(
            url = absoluteUrl(playerPage.finalUrl, "/api/parse"),
            body = body,
            headers = mapOf(
                "Origin" to URI(playerPage.finalUrl).let { "${it.scheme}://${it.authority}" },
                "Referer" to playerPage.finalUrl,
            ),
        )
        return extractJsonStringField(response, "url")?.takeIf(::isMediaUrl)
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        isHhjxPlayerPage(url) -> SourceWebResourceMatch.LoadPage
        else -> SourceWebResourceMatch.Continue
    }

    private fun isHhjxPlayerPage(url: String): Boolean {
        return url.contains("hhjx.hhplayer.com", ignoreCase = true) &&
            url.contains("?url=", ignoreCase = true)
    }
}
