/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.source.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Metadata advertised by an executable source plugin. */
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
    /** Subtitle language identifiers advertised for resources returned by this source. */
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

/** A subject returned by a source, identified by the site's stable identifier. */
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
data class SourceSearchRequest(
    val query: String,
    val limit: Int = 20,
)

@Serializable
data class SourceResolveRequest(
    val subjectId: String,
    val channelId: String,
    val episodeId: String,
    val episodeSort: Float? = null,
    val episodeEp: String? = null,
    /** Filled by the host when routing a request through the shared registry. */
    val pluginId: String = "",
)

/** The state returned by a non-mutating source health check. */
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

/**
 * Stable logical identity for one subject/channel/episode before a short-lived media URL is
 * resolved. The length-prefixed representation is unambiguous even when site IDs contain `/`.
 */
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

/** Request metadata that must travel with a short-lived resolved media URL. */
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

/**
 * A resolved media URL plus the complete request context needed by both player and downloader.
 * The URL is intentionally resolved at use time and is not the logical media identity.
 */
@Serializable
data class ResolvedMedia(
    val stableIdentity: String,
    val url: String,
    val format: ResolvedMediaFormat,
    val headers: Map<String, String> = emptyMap(),
    val originalPageUrl: String? = null,
    val expiresAt: Instant? = null,
    val mimeType: String? = null,
    /** Explicit cookie/referrer/origin fields complement headers for runtimes that need them separately. */
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

/** Host services deliberately remain interfaces so plugins do not depend on app-data internals. */
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
)

data class SourceHttpResponse(
    val statusCode: Int,
    val finalUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {
    fun bodyAsText(): String = body.decodeToString()
}

/** Describes a resource that a plugin wants the host WebView to handle. */
sealed interface SourceWebResourceMatch {
    data object Continue : SourceWebResourceMatch

    data object LoadPage : SourceWebResourceMatch

    data class Matched(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : SourceWebResourceMatch
}

/** Runtime entry point named by a plugin manifest and instantiated by the host loader. */
interface SourcePluginEntryPoint {
    fun create(context: SourcePluginContext): SourcePlugin
}

interface SourcePlugin : AutoCloseable {
    val metadata: SourcePluginMetadata

    suspend fun checkConnection(): SourceConnectionStatus

    suspend fun search(request: SourceSearchRequest): List<SourceSubject>

    suspend fun getSubject(subjectId: String): SourceSubjectDetails

    suspend fun resolve(request: SourceResolveRequest): ResolvedMedia

    /**
     * Matches a resource requested by an external player page.
     *
     * This hook observes normal WebView requests only. It does not decrypt protected streams,
     * bypass access controls, or manufacture a URL that the site did not request.
     */
    fun matchWebResource(url: String): SourceWebResourceMatch = SourceWebResourceMatch.Continue

    override fun close()
}
