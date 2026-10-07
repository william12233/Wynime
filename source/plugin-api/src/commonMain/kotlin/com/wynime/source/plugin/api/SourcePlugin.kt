package com.wynime.source.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class SourcePluginMetadata(
    val id: String,
    val displayName: String,
    val version: String,
    val website: String,
    val description: String = "",
    val iconUrl: String? = null,
    val pluginApiVersion: Int,
    val minHostVersion: String,
    val supportedPlatforms: Set<SourcePluginPlatform>,

    val defaultSubtitleLanguageIds: List<String> = listOf("zh-Hans"),
)

@Serializable
enum class SourcePluginPlatform {
    @SerialName("android")
    ANDROID,
    @SerialName("desktop")
    DESKTOP,
    @SerialName("ios")
    IOS,
}

@Serializable
data class SourceSubject(
    val id: String,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val detailUrl: String? = null,
    val coverUrl: String? = null,
)

@Serializable
data class SourceChannel(
    val id: String,
    val displayName: String,
)

@Serializable
data class SourceEpisode(
    val id: String,
    val displayName: String,
    val episodeSort: Float? = null,
    val episodeEp: String? = null,
    val playPageUrl: String? = null,
    val resourceId: String? = null,
)

@Serializable
data class SourceChannelEpisodes(
    val channel: SourceChannel,
    val episodes: List<SourceEpisode>,
)

@Serializable
data class SourceSubjectDetails(
    val subject: SourceSubject,
    val channels: List<SourceChannelEpisodes>,
)

@Serializable
enum class SourceTracePhase {
    DISCOVERY_START,
    SEARCH_REQUEST,
    SEARCH_RESPONSE,
    MATCH_RESULT,
    SUBJECT_RESOLVE,
    EPISODE_FETCH,
    EPISODE_MATCH,
    PLAY_RESOLVE,
    FINAL_MEDIA_CHECK,
    DOWNLOAD_RESOLVE,
    PLAYBACK_RESULT,
}

@Serializable
enum class SourceResultStatus {
    SUCCESS,
    SUBJECT_NO_MATCH,
    EPISODE_NO_MATCH,
    TIMEOUT,
    NETWORK_ERROR,
    HTTP_ERROR,
    BLOCKED_BY_CHALLENGE,
    AUTH_REQUIRED,
    PARSE_ERROR,
    RESOLVE_ERROR,
    MEDIA_UNREACHABLE,
    PLAYBACK_ERROR,
    PLUGIN_ERROR,
}

@Serializable
data class SourceDiagnostics(
    val traceId: String,
    val provider: String,
    val entryPoint: String,
    val query: String? = null,
    val url: String? = null,
    val domain: String? = null,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val redirectCount: Int? = null,
    val elapsedMillis: Long? = null,
    val responseCategory: SourceResultStatus,
    val userAgentProfile: String? = null,
    val refererPresent: Boolean? = null,
    val cookieNames: List<String> = emptyList(),
    val challengeDetected: String? = null,
    val parserResultCount: Int? = null,
    val matcherScore: Int? = null,
    val failureReason: String? = null,
)

@Serializable
data class SourceSearchRequest(
    val query: String,
    val limit: Int = 20,
    val traceId: String = "",
    val entryPoint: String = "search",
) {

    @Deprecated("Legacy Plugin API constructor", level = DeprecationLevel.WARNING)
    constructor(
        query: String,
        limit: Int = 20,
    ) : this(query, limit, "", "search")
}

@Serializable
data class SourceResolveRequest(
    val subjectId: String,
    val channelId: String,
    val episodeId: String,
    val episodeSort: Float? = null,
    val episodeEp: String? = null,

    val pluginId: String = "",
    val traceId: String = "",
    val entryPoint: String = "resolve",
) {

    @Deprecated("Legacy Plugin API constructor", level = DeprecationLevel.WARNING)
    constructor(
        subjectId: String,
        channelId: String,
        episodeId: String,
        episodeSort: Float? = null,
        episodeEp: String? = null,
        pluginId: String = "",
    ) : this(subjectId, channelId, episodeId, episodeSort, episodeEp, pluginId, "", "resolve")
}

@Serializable
data class SourceConnectionStatus(
    val state: SourceConnectionState,
    val message: String? = null,
)

@Serializable
enum class SourceConnectionState {
    CONNECTED,
    AUTH_REQUIRED,
    BLOCKED,
    FAILED,
}

@Serializable
data class SourceMediaIdentity(
    val pluginId: String,
    val subjectId: String,
    val channelId: String,
    val episodeId: String,
) {
    init {
        require(pluginId.isNotBlank()) { "pluginId must not be blank" }
        require(subjectId.isNotBlank()) { "subjectId must not be blank" }
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }
    }

    fun asStableId(): String = buildString {
        appendComponent(pluginId)
        appendComponent(subjectId)
        appendComponent(channelId)
        appendComponent(episodeId)
    }

    private fun StringBuilder.appendComponent(value: String) {
        append(value.length).append(':').append(value)
    }
}

fun SourceResolveRequest.mediaIdentity(pluginId: String): SourceMediaIdentity = SourceMediaIdentity(
    pluginId = pluginId,
    subjectId = subjectId,
    channelId = channelId,
    episodeId = episodeId,
)

@Serializable
enum class ResolvedMediaFormat {
    HLS,
    MP4,
    WEB,
    UNKNOWN,
}

@Serializable
data class SourceMediaRequestContext(
    val headers: Map<String, String> = emptyMap(),
    val cookies: Map<String, String> = emptyMap(),
    val referrer: String? = null,
    val origin: String? = null,
) {
    fun toHeaders(): Map<String, String> = buildMap {
        putAll(headers)
        if (referrer != null && keys.none { it.equals("Referer", ignoreCase = true) }) {
            put("Referer", referrer)
        }
        if (origin != null && keys.none { it.equals("Origin", ignoreCase = true) }) {
            put("Origin", origin)
        }
        if (cookies.isNotEmpty() && keys.none { it.equals("Cookie", ignoreCase = true) }) {
            put("Cookie", cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
        }
    }
}

@Serializable
data class ResolvedMedia(
    val stableIdentity: String,
    val url: String,
    val format: ResolvedMediaFormat,
    val headers: Map<String, String> = emptyMap(),
    val originalPageUrl: String? = null,
    val expiresAt: Instant? = null,
    val mimeType: String? = null,

    val requestContext: SourceMediaRequestContext = SourceMediaRequestContext(headers = headers),
) {
    init {
        require(stableIdentity.isNotBlank()) { "stableIdentity must not be blank" }
        require(url.isNotBlank()) { "url must not be blank" }
    }

    fun requestHeaders(): Map<String, String> = buildMap {
        putAll(headers)
        putAll(requestContext.toHeaders())
    }
}

interface SourcePluginContext {
    val pluginId: String
    val hostVersion: String
    val platform: SourcePluginPlatform
    val http: SourceHttpClient
    val logger: SourcePluginLogger
}

interface SourcePluginLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}

interface SourceHttpClient {
    suspend fun execute(request: SourceHttpRequest): SourceHttpResponse
}

data class SourceHttpRequest(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val traceId: String = "",
    val entryPoint: String = "http",
) {

    @Deprecated("Legacy Plugin API constructor", level = DeprecationLevel.WARNING)
    constructor(
        method: String = "GET",
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ) : this(method, url, headers, body, "", "http")
}

data class SourceHttpResponse(
    val statusCode: Int,
    val finalUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    val elapsedMillis: Long? = null,
    val redirectCount: Int? = null,
    val contentType: String? = null,
) {

    @Deprecated("Legacy Plugin API constructor", level = DeprecationLevel.WARNING)
    constructor(
        statusCode: Int,
        finalUrl: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0),
    ) : this(statusCode, finalUrl, headers, body, null, null, null)

    fun bodyAsText(): String = body.decodeToString()
}

sealed interface SourceWebResourceMatch {
    data object Continue : SourceWebResourceMatch

    data object LoadPage : SourceWebResourceMatch

    data class Matched(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : SourceWebResourceMatch
}

interface SourcePluginEntryPoint {
    fun create(context: SourcePluginContext): SourcePlugin
}

interface SourcePlugin : AutoCloseable {
    val metadata: SourcePluginMetadata

    suspend fun checkConnection(): SourceConnectionStatus

    suspend fun search(request: SourceSearchRequest): List<SourceSubject>

    suspend fun getSubject(subjectId: String): SourceSubjectDetails

    suspend fun resolve(request: SourceResolveRequest): ResolvedMedia

    fun matchWebResource(url: String): SourceWebResourceMatch = SourceWebResourceMatch.Continue

    override fun close()
}
