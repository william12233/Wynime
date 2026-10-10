package tw.wynime.sources.baimao

import com.wynime.source.plugin.api.SourceHttpRequest
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
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants
import kotlin.coroutines.cancellation.CancellationException

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
        val mediaUrl = resolvePlayInfo(pageUrl, request)
        resolvedMedia(
            request = request,
            pageUrl = pageUrl,
            rawUrl = mediaUrl,
            headers = mapOf("Referer" to pageUrl),
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

    private suspend fun resolvePlayInfo(
        pageUrl: String,
        request: SourceResolveRequest,
    ): String? {
        return try {
            val pageResponse = context.http.execute(
                SourceHttpRequest(
                    method = "GET",
                    url = pageUrl,
                    headers = defaultHeaders,
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ),
            )
            if (pageResponse.statusCode !in 200..399) return null
            val cookies = extractSiteCookies(pageResponse.headers)
            if (cookies["t1"].isNullOrBlank() || cookies["k1"].isNullOrBlank()) return null
            val pageFinalUrl = pageResponse.finalUrl
            val cookieHeader = cookies.asCookieHeader()
            val timeResponse = context.http.execute(
                SourceHttpRequest(
                    method = "GET",
                    url = "$rootUrl/time",
                    headers = defaultHeaders + mapOf(
                        "Cookie" to cookieHeader,
                        "Referer" to pageFinalUrl,
                    ),
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ),
            )
            if (timeResponse.statusCode !in 200..399) return null
            val serverTime = timeResponse.bodyAsText().trim().toDoubleOrNull()?.toLong() ?: return null
            val nowMillis = currentTimeMillis()
            val nowSeconds = nowMillis / 1000L
            val browserFactor = 1
            cookies["m2t"] = m2t(nowSeconds, browserFactor)
            cookies["im"] = im(serverTime, browserFactor)
            cookies["kr"] = kr(serverTime)
            cookies["k2"] = k2(cookies.getValue("t1"))
            cookies["t2"] = t2(cookies.getValue("k2"), nowMillis)

            val playInfoResponse = context.http.execute(
                SourceHttpRequest(
                    method = "GET",
                    url = "$rootUrl/playinfo?aid=${request.subjectId}&playindex=${request.channelId}&epindex=${request.episodeId}&r=${nowMillis}",
                    headers = defaultHeaders + mapOf(
                        "Cookie" to cookies.asCookieHeader(),
                        "Referer" to pageFinalUrl,
                        "X-Requested-With" to "XMLHttpRequest",
                    ),
                    traceId = request.traceId,
                    entryPoint = request.entryPoint,
                ),
            )
            if (playInfoResponse.statusCode !in 200..399) return null
            val payload = decodePlayInfo(playInfoResponse.bodyAsText()) ?: return null
            extractJsonStringField(payload, "vurl")?.takeIf(::isMediaUrl)
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

    private fun decodePlayInfo(raw: String): String? {
        val value = raw.trim()
        if (value.contains('{')) return value
        if (value.isEmpty() || value.length % 2 != 0 || !HEX_PAYLOAD.matches(value)) return null
        val output = StringBuilder(value.length / 2)
        val pairCount = value.length / 2
        for (index in value.indices step 2) {
            val encoded = value.substring(index, index + 2).toIntOrNull(16) ?: return null
            val reverseIndex = pairCount - 1 - (index / 2)
            val decoded = (encoded + 0x100000 - 0x943 - reverseIndex) % 0x100
            output.insert(0, decoded.toChar())
        }
        return output.toString()
    }

    private fun extractSiteCookies(headers: Map<String, String>): LinkedHashMap<String, String> {
        val cookies = linkedMapOf<String, String>()
        val pattern = Regex("(?i)(?:^|[,;]\\s*)(t1|k1)=([^;,\\s]+)")
        headers.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .forEach { (_, value) ->
                pattern.findAll(value).forEach { match ->
                    cookies[match.groupValues[1].lowercase()] = match.groupValues[2]
                }
            }
        return cookies
    }

    private fun LinkedHashMap<String, String>.asCookieHeader(): String = entries.joinToString("; ") {
        "${it.key}=${it.value}"
    }

    private fun m2t(seconds: Long, browserFactor: Int): String {
        val value = seconds shr (0x11 + browserFactor)
        return (
            (((((value * 0x11 + 0xbd) + (value % 0x3f + 0xf)) * (value % 0x1f + 0x21)) *
                (value % 0x11 + 0x70)) * (value % 0x7 + 0x43)) + 0x17b
            ).toString()
    }

    private fun im(seconds: Long, browserFactor: Int): String = challengeValue(
        seconds shr (0x11 + browserFactor),
    )

    private fun kr(seconds: Long): String = challengeValue(seconds shr 0x7)

    private fun challengeValue(value: Long): String = (
        ((value * 0xd + 0x113) * (value % 0x21 + 0xb) + (value % 0x11 + 0x3)) % 0x387
        ).toString()

    private fun k2(t1: String): String {
        val value = (t1.toLongOrNull() ?: return "") shr 0x5
        return (
            (((value * (value % 0xff + 0x3) + 0x6c2b) * (value % 0x81 + 0xb)) *
                (value % 0xf + 0x7)) + value
            ).toString()
    }

    private fun t2(k2: String, initialMillis: Long): String {
        val suffix = k2.lastOrNull() ?: return initialMillis.toString()
        var millis = initialMillis
        while (!millis.toString().takeLast(3).contains(suffix)) millis++
        return millis.toString()
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    private companion object {
        val HEX_PAYLOAD = Regex("(?i)^[0-9a-f]+$")
    }
}
