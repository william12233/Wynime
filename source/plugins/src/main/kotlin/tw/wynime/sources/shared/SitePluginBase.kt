package tw.wynime.sources.shared

import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceConnectionState
import com.wynime.source.plugin.api.SourceConnectionStatus
import com.wynime.source.plugin.api.SourceEpisode
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceMediaIdentity
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginMetadata
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.source.plugin.api.mediaIdentity
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

internal data class HtmlLink(
    val href: String,
    val text: String,
    val attributes: String,
)

internal data class Page(
    val requestedUrl: String,
    val finalUrl: String,
    val statusCode: Int,
    val html: String,
) {
    val title: String
        get() = Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
}

internal abstract class SitePluginBase(
    protected val context: SourcePluginContext,
    id: String,
    displayName: String,
    protected val rootUrl: String,
    iconUrl: String,
    description: String,
) : SourcePlugin {
    final override val metadata = SourcePluginMetadata(
        id = id,
        displayName = displayName,
        version = pluginVersionFor(context.pluginId),
        website = rootUrl,
        description = description,
        iconUrl = iconUrl,
        pluginApiVersion = PLUGIN_API_VERSION,
        minHostVersion = MIN_HOST_VERSION,
        supportedPlatforms = SUPPORTED_PLATFORMS,
        defaultSubtitleLanguageIds = listOf("zh-Hans"),
    )

    override suspend fun checkConnection(): SourceConnectionStatus = try {
        val page = requestPage(rootUrl)
        if (page.statusCode in 200..399) {
            SourceConnectionStatus(SourceConnectionState.CONNECTED)
        } else {
            SourceConnectionStatus(
                SourceConnectionState.BLOCKED,
                "站點回應 HTTP ${page.statusCode}",
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: LinkageError) {
        throw error
    } catch (error: Error) {
        throw error
    } catch (error: Throwable) {
        SourceConnectionStatus(SourceConnectionState.FAILED, error.message)
    }

    protected suspend fun requestPage(
        url: String,
        headers: Map<String, String> = emptyMap(),
        traceId: String = "",
        entryPoint: String = "http",
    ): Page {
        val response = context.http.execute(
            SourceHttpRequest(
                method = "GET",
                url = url,
                headers = defaultHeaders + headers,
                traceId = traceId,
                entryPoint = entryPoint,
            ),
        )
        if (response.statusCode !in 200..399) {
            throw SourceSiteException("${metadata.displayName} 回應 HTTP ${response.statusCode}: $url")
        }
        return Page(url, response.finalUrl, response.statusCode, response.bodyAsText())
    }

    protected suspend fun requestJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        traceId: String = "",
        entryPoint: String = "http",
    ): String {
        val response = context.http.execute(
            SourceHttpRequest(
                method = "POST",
                url = url,
                headers = defaultHeaders + mapOf("Content-Type" to "application/json") + headers,
                body = body.encodeToByteArray(),
                traceId = traceId,
                entryPoint = entryPoint,
            ),
        )
        if (response.statusCode !in 200..399) {
            throw SourceSiteException("${metadata.displayName} 回應 HTTP ${response.statusCode}: $url")
        }
        return response.bodyAsText()
    }

    protected fun subject(
        id: String,
        title: String,
        detailUrl: String?,
        coverUrl: String? = null,
    ) = SourceSubject(
        id = id,
        title = cleanText(title).ifBlank { id },
        detailUrl = detailUrl,
        coverUrl = coverUrl,
    )

    protected fun episode(
        id: String,
        title: String,
        pageUrl: String,
        episodeSort: Float? = null,
    ) = SourceEpisode(
        id = id,
        displayName = cleanText(title).ifBlank { id },
        episodeSort = episodeSort ?: parseEpisodeNumber(title),
        episodeEp = episodeSort?.toString(),
        playPageUrl = pageUrl,
    )

    protected fun resolvedMedia(
        request: com.wynime.source.plugin.api.SourceResolveRequest,
        pageUrl: String,
        rawUrl: String?,
        headers: Map<String, String> = emptyMap(),
        referer: String? = pageUrl,
    ): ResolvedMedia {
        val decodedUrl = rawUrl?.let(::decodePlayerUrl)
        val webUrl = decodedUrl?.takeIf(::isHttpUrl)
        val directUrl = webUrl?.takeIf(::isDirectMediaUrl)
        val format = directUrl?.let(::directMediaFormat) ?: ResolvedMediaFormat.WEB
        val requestHeaders = (referer?.let { mapOf("Referer" to it) } ?: emptyMap()) + headers
        return ResolvedMedia(
            stableIdentity = request.mediaIdentity(metadata.id).asStableId(),
            url = directUrl ?: webUrl ?: pageUrl,
            format = format,
            headers = requestHeaders,
            originalPageUrl = pageUrl,
        )
    }

    protected fun dynamicSearchLinks(
        html: String,
        query: String,
        detailPattern: Regex,
    ): List<SourceSubject> {
        val candidates = links(html).mapNotNull { link ->
            val match = detailPattern.find(link.href) ?: return@mapNotNull null
            val id = match.groupValues.getOrNull(1)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = link.text.ifBlank { match.groupValues.getOrNull(2).orEmpty() }
            SearchLink(id, title, absoluteUrl(rootUrl, link.href), link.attributes)
        }
            .groupBy { it.id }
            .values
            .mapNotNull { it.maxByOrNull(::searchLinkScore) }

        val matching = candidates.filter { queryMatches(it.title, query) }
        return (if (query.isBlank()) candidates else matching)
            .take(20)
            .map { subject(it.id, it.title, it.url) }
    }

    protected fun parsePlayerUrl(html: String): String? = extractJsonStringField(html, "url")
        ?: Regex("(?is)\\b(?:data-play|data-url|data-src)\\s*=\\s*['\"]([^'\"]+)")
            .find(html)?.groupValues?.getOrNull(1)

    protected fun defaultWebResourceMatch(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        isPlayerPageUrl(url) -> SourceWebResourceMatch.LoadPage
        else -> SourceWebResourceMatch.Continue
    }

    protected open fun isDirectMediaUrl(url: String): Boolean = isMediaUrl(url)

    protected open fun directMediaFormat(url: String): ResolvedMediaFormat = mediaFormat(url)

    protected fun absoluteUrl(base: String, raw: String): String {
        val value = decodeHtmlEntities(raw.trim())
        return try {
            URI(base).resolve(value).toString()
        } catch (_: IllegalArgumentException) {
            value
        }
    }

    protected fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    override fun close() = Unit

    protected open val defaultHeaders: Map<String, String> = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language" to "zh-TW,zh;q=0.9,en;q=0.7",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0 Safari/537.36",
    )

    private fun isPlayerPageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (Regex("(?i)\\.(?:js|css|png|jpe?g|gif|svg|webp|woff2?|ttf)(?:[?#]|$)").containsMatchIn(lower)) {
            return false
        }
        return lower.contains("/vip/") ||
            lower.contains("player") ||
            lower.contains("hhplayer") ||
            lower.contains("?url=")
    }

    private fun searchLinkScore(link: SearchLink): Int {
        val attributes = link.attributes.lowercase()
        val semanticClass = if (
            "title" in attributes || "name" in attributes || "exp" in attributes || "text" in attributes
        ) 1000 else 0
        val presentationOnly = presentationOnlySuffix.matches(cleanText(link.title))
        val statusOnly = Regex("(?i)^(?:\\d+(?:\\.\\d+)?分\\s*)?(?:已完结|完结|更新至[^ ]*|播放正片|立即播放|详情\\s*>?)$")
            .matches(cleanText(link.title))
        val statusPenalty = if (statusOnly) 500 else 0
        val presentationPenalty = if (presentationOnly) 1500 else 0
        return semanticClass + link.title.length - statusPenalty - presentationPenalty
    }
}

private data class SearchLink(
    val id: String,
    val title: String,
    val url: String,
    val attributes: String,
)

internal class SourceSiteException(message: String) : Exception(message)

internal fun links(html: String): Sequence<HtmlLink> = Regex(
    "(?is)<a\\b([^>]*?)\\bhref\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
).findAll(html).map { match ->
    val fallbackTitle = Regex("(?i)\\b(?:title|alt)\\s*=\\s*[\"']([^\"']+)[\"']")
        .find(match.value)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val linkText = cleanText(match.groupValues[3])
    val fallbackText = cleanText(fallbackTitle)
    HtmlLink(
        href = decodeHtmlEntities(match.groupValues[2]),
        text = normalizeLinkText(linkText, fallbackText),
        attributes = match.groupValues[1],
    )
}

private fun isStatusOnlyLinkText(value: String): Boolean = Regex(
    "(?i)^(?:\\d+(?:\\.\\d+)?分\\s*)?(?:已完结|完结|更新至[^ ]*|播放正片|立即播放|详情\\s*>?)(?:\\s*[\\p{C}\\p{So}]*)$",
).matches(value)

private val presentationOnlySuffix = Regex("(?i)\\s*(?:封面图|封面圖)\\s*$")

internal fun normalizePresentationTitle(value: String): String =
    value.replace(presentationOnlySuffix, "").trim()

private fun normalizeLinkText(linkText: String, fallbackText: String): String {
    val normalizedFallback = normalizePresentationTitle(fallbackText)
    return when {
        linkText.isBlank() -> normalizedFallback
        isStatusOnlyLinkText(linkText) && normalizedFallback.isNotBlank() -> normalizedFallback
        presentationOnlySuffix.matches(linkText) && normalizedFallback.isNotBlank() -> normalizedFallback
        presentationOnlySuffix.containsMatchIn(linkText) -> linkText.replace(presentationOnlySuffix, "").trim()
        else -> linkText
    }
}

internal fun cleanText(value: String): String = decodeHtmlEntities(
    value.replace(Regex("(?is)<[^>]+>"), " ")
        .replace(Regex("\\s+"), " "),
).trim()

internal fun queryMatches(title: String, query: String): Boolean {
    val normalizedTitle = normalizeSearchText(title)
    return searchQueryVariants(query).any { variant ->
        val normalizedQuery = normalizeSearchText(variant)
        normalizedQuery.isBlank() || normalizedTitle.contains(normalizedQuery)
    }
}

internal fun searchQueryVariants(query: String): List<String> {
    if (query.isBlank()) return listOf(query)
    val result = linkedSetOf<String>()
    fun add(value: String?) {
        value?.trim()?.takeIf { it.isNotBlank() }?.let(result::add)
    }

    add(query)
    add(query.replace(Regex("\\s+"), " "))

    val withoutArc = query.replace(
        Regex("(?i)\\s*(?:丧失篇|喪失篇|夺还篇|奪還篇|奪還編|失落篇|失落編)\\s*$"),
        "",
    )
    add(withoutArc)
    Regex("(?i)^(.+?第\\s*\\d+\\s*季)(?:\\s+.*)?$").find(withoutArc)?.groupValues?.getOrNull(1)?.let(::add)
    Regex("(?i)^(.+?\\d+(?:st|nd|rd|th)\\s+season)(?:\\s+.*)?$")
        .find(withoutArc)?.groupValues?.getOrNull(1)?.let(::add)

    val lower = query.lowercase()
    val isReZero = lower.contains("re0") ||
        lower.contains("re:zero") ||
        lower.contains("re：zero") ||
        lower.contains("re zero") ||
        lower.contains("starting life in another world") ||
        query.contains("从零开始") ||
        query.contains("從零開始") ||
        query.contains("リゼロ")
    if (isReZero) {
        val season = Regex("第\\s*(\\d+)\\s*季").find(query)?.groupValues?.getOrNull(1)
            ?: Regex("(?i)(\\d+)(?:st|nd|rd|th)\\s+season").find(query)?.groupValues?.getOrNull(1)
        val suffix = season?.let { " 第${it}季" }.orEmpty()
        add("Re：从零开始的异世界生活$suffix")
        add("从零开始的异世界生活$suffix")
        add("Re:Zero$suffix")
        add("Re：Zero$suffix")
        add("re0")
        add("rezero")
    }
    return result.toList()
}

private fun normalizeSearchText(value: String): String = value
    .lowercase()
    .replace("：", ":")
    .replace(Regex("\\s+"), "")

internal fun decodeHtmlEntities(value: String): String = value
    .replace("&nbsp;", " ", ignoreCase = true)
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)
    .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
    .replace(Regex("&#x([0-9a-f]+);", RegexOption.IGNORE_CASE)) {
        it.groupValues[1].toInt(16).toChar().toString()
    }

internal fun parseEpisodeNumber(value: String): Float? = Regex("(?i)(?:第\\s*)?(\\d+(?:\\.\\d+)?)")
    .find(cleanText(value))?.groupValues?.getOrNull(1)?.toFloatOrNull()

internal fun extractJsonStringField(text: String, field: String): String? {
    val pattern = Regex(
        """(?s)(?:\\)?[\"']${Regex.escape(field)}(?:\\)?[\"']\s*:\s*(?:\\)?[\"']((?:\\.|[^\"'\\])*)(?:\\)?[\"']""",
    )
    return pattern.find(text)?.groupValues?.getOrNull(1)?.let(::decodeJsonString)
}

internal fun jsonObjects(text: String): List<String> {
    val objects = mutableListOf<String>()
    var start = -1
    var depth = 0
    var inString = false
    var escaped = false
    text.forEachIndexed { index, char ->
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            return@forEachIndexed
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) start = index
                depth++
            }
            '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    objects += text.substring(start, index + 1)
                    start = -1
                }
            }
        }
    }
    return objects
}

internal fun jsonArrayObjects(text: String, field: String): List<String> {
    val arrayStart = Regex(
        """(?s)(?:\\)?[\"']${Regex.escape(field)}(?:\\)?[\"']\s*:\s*\[""",
    ).find(text)?.range?.last?.plus(1) ?: return emptyList()
    val objects = mutableListOf<String>()
    var start = -1
    var depth = 0
    var inString = false
    var escaped = false
    for (index in arrayStart until text.length) {
        val char = text[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) start = index
                depth++
            }
            '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    objects += text.substring(start, index + 1)
                    start = -1
                }
            }
            ']' -> if (depth == 0) return objects
        }
    }
    return objects
}

internal fun extractJsonNumberField(text: String, field: String): Int? = Regex(
    """(?s)(?:\\)?[\"']${Regex.escape(field)}(?:\\)?[\"']\s*:\s*(\d+)""",
).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal fun extractPlayerObjectUrl(text: String): String? {
    val marker = Regex("(?is)(?:player_aaaa|player|videoData)\\s*=\\s*\\{").find(text) ?: return null
    val start = marker.range.last
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val char = text[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return extractJsonStringField(text.substring(start, index + 1), "url")
                }
            }
        }
    }
    return null
}

internal fun decodeJsonString(value: String): String = value
    .replace(Regex("\\\\u([0-9a-fA-F]{4})")) {
        it.groupValues[1].toInt(16).toChar().toString()
    }
    .replace("\\\"", "\"")
    .replace("\\/", "/")
    .replace("\\n", "\n")
    .replace("\\r", "\r")
    .replace("\\t", "\t")
    .replace("\\\\", "\\")

internal fun decodePlayerUrl(raw: String): String? {
    var value = raw.trim().trim('"', '\'')
    repeat(3) {
        val decoded = try {
            URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            return@repeat
        }
        if (decoded == value) return@repeat
        value = decoded
    }
    extractMediaQueryUrl(value)?.let { return it }
    extractHttpUrl(value)?.let { return it }

    val candidates = listOf(value, raw).distinct()
    for (candidate in candidates) {
        val compact = candidate.replace(Regex("\\s+"), "")
        for (offset in 0..minOf(4, compact.lastIndex)) {
            val encoded = compact.substring(offset)
            val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
            val decoded = try {
                String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                continue
            }
            extractMediaQueryUrl(decoded)?.let { return it }
            extractHttpUrl(decoded)?.let { return it }
            val urlDecoded = try {
                URLDecoder.decode(decoded, Charsets.UTF_8.name())
            } catch (_: IllegalArgumentException) {
                null
            }
            extractMediaQueryUrl(urlDecoded.orEmpty())?.let { return it }
            extractHttpUrl(urlDecoded.orEmpty())?.let { return it }
        }
    }
    return value.takeIf(::isHttpUrl)
}

internal fun extractMediaQueryUrl(value: String): String? = Regex(
    "(?i)(?:[?&](?:url|file|src|play)=)([^&#\\\"'<>]+)",
).findAll(value).mapNotNull { match ->
    val candidate = try {
        URLDecoder.decode(match.groupValues[1].replace("+", "%2B"), Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        return@mapNotNull null
    }
    extractHttpUrl(candidate)?.takeIf(::isMediaUrl)
}.firstOrNull()

internal fun extractHttpUrl(value: String): String? = Regex("https?://[^\\s\"'<>\\\\]+", RegexOption.IGNORE_CASE)
    .find(value)?.value?.trimEnd('.', ',', ';')

internal fun mediaFormat(url: String): ResolvedMediaFormat = when {
    Regex("(?i)\\.m3u8(?:[?#]|$)").containsMatchIn(url) || url.contains("m3u8", ignoreCase = true) ->
        ResolvedMediaFormat.HLS
    Regex("(?i)\\.(?:mp4|m4v)(?:[?#]|$)").containsMatchIn(url) -> ResolvedMediaFormat.MP4
    else -> ResolvedMediaFormat.UNKNOWN
}

internal fun isMediaUrl(url: String): Boolean = Regex(
    "(?i)(?:\\.m3u8|\\.mp4|\\.m4v)(?:[?#]|$)",
).containsMatchIn(url) || url.contains("m3u8", ignoreCase = true)

internal fun isHttpUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

internal const val PLUGIN_API_VERSION = 3
internal const val MIN_HOST_VERSION = "0.1.3"
internal val SUPPORTED_PLATFORMS = setOf(
    com.wynime.source.plugin.api.SourcePluginPlatform.DESKTOP,
    com.wynime.source.plugin.api.SourcePluginPlatform.ANDROID,
)
