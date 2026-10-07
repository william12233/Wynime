package tw.wynime.sources.rk2

import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourceMediaRequestContext
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.queryMatches
import tw.wynime.sources.shared.searchQueryVariants

class Rk2EntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = Rk2Plugin(context)
}

internal class Rk2Plugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "2rk",
    displayName = "二礦動漫",
    rootUrl = "https://www.2rk.cc",
    iconUrl = "https://www.2rk.cc/logo.png",
    description = "二礦動漫公開 HLS 番劇來源",
) {

    override val defaultHeaders: Map<String, String> = super.defaultHeaders + (
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
        )

    private val fallbackChannels = listOf(
        Rk2Channel(host = "www.2rk.cc", displayName = "线路1"),
        Rk2Channel(host = "v1.2rk.cc", displayName = "线路2"),
        Rk2Channel(host = "v2.2rk.cc", displayName = "线路3"),
    )

    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
        val results = mutableListOf<SourceSubject>()
        for (variant in searchQueryVariants(request.query)) {
            val page = requestPage(
                "$rootUrl/search?w=${urlEncode(variant)}",
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            val pageResults: List<SourceSubject> = links(page.html).mapNotNull { link ->
                val match = Regex("(?i)/detail/([^/?#]+)(?:\\?id=([^&#\"']+))?").find(link.href)
                    ?: return@mapNotNull null
                val slug = match.groupValues[1]
                val currentEpisode = match.groupValues.getOrNull(2).orEmpty().ifBlank { "1" }
                val id = "$slug#$currentEpisode"
                subject(id, link.text, absoluteUrl(rootUrl, link.href))
            }
                .filter { queryMatches(it.title, variant) }
                .toList()
            results += pageResults
            if (results.distinctBy { it.id }.size >= request.limit) break
        }
        return results.distinctBy { it.id }.take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val slug = subjectId.substringBefore('#')
        val initialEpisode = subjectId.substringAfter('#', "1")
        val page = requestPage("$rootUrl/detail/$slug?id=$initialEpisode")
        val title = Regex("(?is)<h1[^>]*>(.*?)</h1>").find(page.html)?.groupValues?.getOrNull(1)
            ?.let(::cleanText)
            ?.ifBlank { null }
            ?: page.title.substringBefore("-").ifBlank { slug }
        val subject = subject(subjectId, title, page.finalUrl)
        val episodes = Regex("(?is)href=[\"'](?:${Regex.escape(rootUrl)})?/detail/${Regex.escape(slug)}\\?id=(\\d+)[^\"']*[\"'][^>]*>(.*?)</a>")
            .findAll(page.html)
            .map { match ->
                val id = match.groupValues[1]
                episode(
                    id,
                    match.groupValues[2],
                    "$rootUrl/detail/$slug?id=$id",
                    parseEpisodeNumber(match.groupValues[2]) ?: id.toFloatOrNull(),
                )
            }
            .distinctBy { it.id }
            .toList()
            .ifEmpty { listOf(episode(initialEpisode, "第${initialEpisode}集", page.finalUrl)) }
        return SourceSubjectDetails(
            subject = subject,
            channels = loadChannels().map { channel ->
                SourceChannelEpisodes(
                    channel = SourceChannel(channel.host, channel.displayName),
                    episodes = episodes.map { episode ->
                        episode.copy(
                            playPageUrl = "$rootUrl/detail/$slug?id=${episode.id}",
                        )
                    },
                )
            },
        )
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val slug = request.subjectId.substringBefore('#')
        val channel = loadChannels().firstOrNull { it.host == request.channelId }
            ?: throw IllegalArgumentException("Unknown 2RK channel: ${request.channelId}")
        val pageUrl = "$rootUrl/detail/$slug?id=${request.episodeId}"
        val lineHeaders = mapOf(
            "Accept" to "*/*",
            "Cookie" to "curXianlu=${channel.host}",
            "User-Agent" to defaultHeaders.getValue("User-Agent"),
        )
        val page = requestPage(
            pageUrl,
            headers = mapOf("Cookie" to "curXianlu=${channel.host}"),
            traceId = request.traceId,
            entryPoint = request.entryPoint,
        )
        val mediaUrl = extractJsonStringField(page.html, "source")
            ?: Regex("(?is)loadSource\\s*\\(\\s*[\"']([^\"']+)[\"']").find(page.html)?.groupValues?.getOrNull(1)
            ?: Regex("https?://[^\"'<>\\s]+\\.m3u8(?:\\?[^\"'<>\\s]*)?").find(page.html)?.value
        val resolved = resolvedMedia(request, page.finalUrl, mediaUrl ?: page.finalUrl, headers = lineHeaders)
        resolved.copy(
            requestContext = SourceMediaRequestContext(
                headers = resolved.headers,
                cookies = mapOf("curXianlu" to channel.host),
                referrer = page.finalUrl,
                origin = rootUrl,
            ),
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        else -> SourceWebResourceMatch.Continue
    }

    private data class Rk2Channel(
        val host: String,
        val displayName: String,
    )

    private suspend fun loadChannels(): List<Rk2Channel> {
        val script = requestPage("$rootUrl/c.js").html
        val hosts = Regex("(?s)\\bchannels\\s*=\\s*\\[([^]]*)]")
            .find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { values ->
                Regex("[\"']([^\"']+)[\"']")
                    .findAll(values)
                    .map { it.groupValues[1].trim() }
                    .filter { it.isNotBlank() }
                    .toList()
            }
            .orEmpty()
        return hosts
            .mapIndexed { index, host -> Rk2Channel(host, "线路${index + 1}") }
            .ifEmpty { fallbackChannels }
    }
}
