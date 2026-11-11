package tw.wynime.sources.rk2

import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourceHttpRequest
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
import tw.wynime.sources.shared.SourceSiteException
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.queryMatches
import tw.wynime.sources.shared.searchQueryVariants
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

class Rk2EntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = Rk2Plugin(context)
}

internal class Rk2Plugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "2rk",
    displayName = "二礦動漫",
    pluginVersion = PLUGIN_VERSION,
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
        try {
            return@run resolveForChannel(request, slug, channel)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (channel.host == PRIMARY_CHANNEL_HOST) throw error
            // v1/v2 are occasionally published in c.js while their upstream
            // edge is down. Keep selecting those lines from making the player
            // wait forever: retry the same episode on the known-good primary
            // edge, with its matching cookie and request context.
            context.logger.warn(
                "2RK ${channel.host} unavailable; falling back to $PRIMARY_CHANNEL_HOST",
                error,
            )
            resolveForChannel(request, slug, Rk2Channel(PRIMARY_CHANNEL_HOST, "线路1"))
        }
    }

    private suspend fun resolveForChannel(
        request: SourceResolveRequest,
        slug: String,
        channel: Rk2Channel,
    ) = run {
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
            ?: Regex("(?is)(?:loadSource|loadSrc)\\s*\\(\\s*[\"']([^\"']+)[\"']")
                .find(page.html)?.groupValues?.getOrNull(1)
            ?: Regex("(?is)(?:data-src|data-url|src)\\s*=\\s*[\"'](https?://[^\"']+\\.m3u8(?:\\?[^\"']*)?)[\"']")
                .find(page.html)?.groupValues?.getOrNull(1)
            ?: Regex("https?://[^\"'<>\\s]+\\.m3u8(?:\\?[^\"'<>\\s]*)?").find(page.html)?.value
        val baseResolved = resolvedMedia(
            request,
            page.finalUrl,
            mediaUrl?.let { absoluteUrl(page.finalUrl, it) } ?: page.finalUrl,
            headers = lineHeaders,
        )
        val resolved = baseResolved.copy(
            requestContext = SourceMediaRequestContext(
                headers = baseResolved.headers,
                cookies = mapOf("curXianlu" to channel.host),
                referrer = page.finalUrl,
                origin = rootUrl,
            ),
        )
        if (resolved.format == ResolvedMediaFormat.HLS) {
            validateHlsMedia(resolved.url, resolved.requestHeaders(), request)
        }
        return@run resolved
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

    private suspend fun validateHlsMedia(
        mediaUrl: String,
        headers: Map<String, String>,
        request: SourceResolveRequest,
    ) {
        val playlistResponse = context.http.execute(
            SourceHttpRequest(
                method = "GET",
                url = mediaUrl,
                headers = headers,
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            ),
        )
        if (playlistResponse.statusCode !in 200..399) {
            throw SourceSiteException(
                "2RK media playlist returned HTTP ${playlistResponse.statusCode}: $mediaUrl",
            )
        }
        val playlist = playlistResponse.bodyAsText()
        if (!playlist.contains("#EXTM3U")) {
            throw SourceSiteException("2RK media URL did not return an HLS playlist: $mediaUrl")
        }

        val keyUrls = playlist.lineSequence()
            .filter { it.startsWith("#EXT-X-KEY", ignoreCase = true) }
            .mapNotNull { line ->
                Regex("(?i)\\bURI\\s*=\\s*[\"']([^\"']+)[\"']")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
            }
            .map { resolveHlsUrl(mediaUrl, it) }
            .distinct()
        for (keyUrl in keyUrls) {
            val keyResponse = context.http.execute(
                SourceHttpRequest(
                    method = "GET",
                    url = keyUrl,
                    headers = headers,
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ),
            )
            if (keyResponse.statusCode !in 200..399) {
                throw SourceSiteException(
                    "2RK HLS key returned HTTP ${keyResponse.statusCode}: $keyUrl",
                )
            }
            if (keyResponse.body.size != AES_128_KEY_BYTES) {
                throw SourceSiteException(
                    "2RK HLS key is ${keyResponse.body.size} bytes; the selected line is currently unavailable",
                )
            }
        }
    }

    private fun resolveHlsUrl(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)

    private companion object {
        const val AES_128_KEY_BYTES = 16
        const val PRIMARY_CHANNEL_HOST = "www.2rk.cc"
    }
}
