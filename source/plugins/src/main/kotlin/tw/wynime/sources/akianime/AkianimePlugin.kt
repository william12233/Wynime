package tw.wynime.sources.akianime

import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.source.plugin.api.mediaIdentity
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.extractPlayerObjectUrl
import tw.wynime.sources.shared.isHttpUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants
import kotlin.coroutines.cancellation.CancellationException

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
        val playerUrl = extractPlayerObjectUrl(page.html)
        val decodedDokiUrl = playerUrl
            ?.takeIf { it.startsWith("Doki-", ignoreCase = true) }
            ?.let { resolveDokiMedia(it, page.finalUrl, request) }
        val rawUrl = decodedDokiUrl ?: playerUrl
        val directDoki = decodedDokiUrl != null && isDokiMediaUrl(decodedDokiUrl)
        if (directDoki) {
            resolvedDokiMedia(request, page.finalUrl, decodedDokiUrl)
        } else {
            resolvedMedia(
                request = request,
                pageUrl = page.finalUrl,
                rawUrl = rawUrl,
                headers = mapOf("Referer" to page.finalUrl),
            )
        }
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isAkiMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
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

    private suspend fun resolveDokiMedia(
        dokiUrl: String,
        pageUrl: String,
        request: SourceResolveRequest,
    ): String? {
        return try {
            val parserUrl = "$DOKI_PARSER_URL/?url=${urlEncode(dokiUrl)}"
            val parserPage = requestPage(
                parserUrl,
                headers = mapOf("Referer" to pageUrl),
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            )
            val key = extractJsonStringField(parserPage.html, "key") ?: return null
            val time = extractJsonStringField(parserPage.html, "time") ?: return null
            val response = context.http.execute(
                SourceHttpRequest(
                    method = "POST",
                    url = "$DOKI_PARSER_URL/api_config.php",
                    headers = defaultHeaders + mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "Referer" to parserUrl,
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                    body = listOf(
                        "url" to dokiUrl,
                        "time" to time,
                        "key" to key,
                        "title" to "",
                    ).joinToString("&") { (name, value) ->
                        "${urlEncode(name)}=${urlEncode(value)}"
                    }.encodeToByteArray(),
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ),
            )
            if (response.statusCode !in 200..399) return null
            val body = response.bodyAsText()
            val code = extractJsonStringField(body, "code")
            val mediaUrl = extractJsonStringField(body, "url")
            mediaUrl
                ?.takeIf { code == "200" && isHttpUrl(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: LinkageError) {
            throw error
        } catch (error: Error) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolvedDokiMedia(
        request: SourceResolveRequest,
        pageUrl: String,
        mediaUrl: String,
    ): ResolvedMedia = ResolvedMedia(
        stableIdentity = request.mediaIdentity(metadata.id).asStableId(),
        url = mediaUrl,
        format = when {
            Regex("(?i)\\.m3u8(?:[?#&]|$)").containsMatchIn(mediaUrl) ||
                mediaUrl.contains("m3u8", ignoreCase = true) -> ResolvedMediaFormat.HLS
            Regex("(?i)\\.(?:mp4|m4v)(?:[?#&]|$)").containsMatchIn(mediaUrl) -> ResolvedMediaFormat.MP4
            else -> ResolvedMediaFormat.UNKNOWN
        },
        originalPageUrl = pageUrl,
    )

    private fun isAkiMediaUrl(url: String): Boolean =
        isMediaUrl(url) || Regex("(?i)\\.(?:mp4|m4v)(?:[?#&]|$)").containsMatchIn(url)

    private fun isDokiMediaUrl(url: String): Boolean = isAkiMediaUrl(url)

    private fun isPlayerPage(url: String): Boolean = Regex(
        "(?i)/bgmplay/[^/?#]+-\\d+-\\d+\\.html(?:[?#]|$)",
    ).containsMatchIn(url)

    private companion object {
        const val DOKI_PARSER_URL = "https://aniplayer.xn--gmqr9gevarqk8t.cn"
    }
}
