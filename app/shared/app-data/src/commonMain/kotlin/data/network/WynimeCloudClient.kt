package com.wynime.app.data.network

import com.wynime.cloud.apis.DefaultApi
import com.wynime.cloud.models.CollectionRemoval
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.ktor.UnsafeScopedHttpClientApi

class WynimeCloudClient(
    private val client: ScopedHttpClient,
) {
    suspend fun createCollectionRemoval(sessionToken: String, subjectId: Int): CollectionRemoval = use { http ->
        DefaultApi(BASE_URL, http).apply { setBearerToken(sessionToken) }
            .createCollectionRemoval(subjectId).body()
    }

    suspend fun confirmCollectionRemoval(sessionToken: String, subjectId: Int): CollectionRemoval = use { http ->
        DefaultApi(BASE_URL, http).apply { setBearerToken(sessionToken) }
            .confirmCollectionRemoval(subjectId).body()
    }

    suspend fun startBangumiOAuth(
        state: String,
        mode: String,
        platform: String,
        arch: String,
        redirectUri: String? = null,
    ): String = use { httpClient ->
        httpClient.get("$BASE_URL/api/v1/oauth/bangumi/start") {
            parameter("state", state)
            parameter("mode", mode)
            parameter("platform", platform)
            parameter("arch", arch)
            redirectUri?.let { parameter("redirectUri", it) }
        }.body<WynimeOAuthStartResponse>().url
    }

    suspend fun pollBangumiOAuthTicket(state: String): String? = try {
        use { httpClient ->
            httpClient.get("$BASE_URL/api/v1/oauth/bangumi/result") {
                parameter("state", state)
            }.body<WynimeOAuthResultResponse>().ticket
        }
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.TooEarly) null else throw e
    }

    suspend fun exchangeBangumiOAuthTicket(ticket: String): WynimeSessionResponse = use { httpClient ->
        httpClient.post("$BASE_URL/api/v1/oauth/bangumi/ticket/exchange") {
            contentType(ContentType.Application.Json)
            setBody(WynimeOAuthTicketExchangeRequest(ticket))
        }.body()
    }

    suspend fun refreshSession(sessionToken: String): WynimeSessionResponse = use { httpClient ->
        httpClient.post("$BASE_URL/api/v1/session/refresh") {
            bearerAuth(sessionToken)
        }.body()
    }

    suspend fun syncPlayback(
        sessionToken: String,
        request: WynimePlaybackSyncRequest,
    ): WynimePlaybackSyncResponse = use { httpClient ->
        httpClient.post("$BASE_URL/api/v1/playback/sync") {
            bearerAuth(sessionToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    @OptIn(UnsafeScopedHttpClientApi::class)
    private suspend fun <T> use(block: suspend (io.ktor.client.HttpClient) -> T): T {
        val ticket = client.borrow()
        try {
            return block(ticket.client)
        } finally {
            client.returnClient(ticket)
        }
    }

    private companion object {
        const val BASE_URL = "https://wynime-bangumi-broker.wzhou785.workers.dev"
    }
}

@Serializable
data class WynimeOAuthStartResponse(
    val url: String,
)

@Serializable
data class WynimeOAuthResultResponse(
    val ticket: String? = null,
    val error: String? = null,
)

@Serializable
private data class WynimeOAuthTicketExchangeRequest(
    val ticket: String,
)

@Serializable
data class WynimeSessionResponse(
    val sessionToken: String,
    val bangumiAccessToken: String,
    val expiresAtMillis: Long,
)

@Serializable
data class WynimePlaybackSyncRequest(
    val deviceId: String,
    val cursor: Long,
    val changes: List<WynimePlaybackChange>,
)

@Serializable
data class WynimePlaybackChange(
    val subjectId: Int,
    val episodeId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAt: Long,
    val baseRevision: Long,
    val deleted: Boolean = false,
)

@Serializable
data class WynimePlaybackSyncResponse(
    val cursor: Long,
    val serverChanges: List<WynimePlaybackServerChange> = emptyList(),
    val accepted: List<WynimePlaybackAcceptedChange> = emptyList(),
)

@Serializable
data class WynimePlaybackServerChange(
    val subjectId: Int,
    val episodeId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastPlayedAt: Long,
    val revision: Long,
    val deleted: Boolean = false,
)

@Serializable
data class WynimePlaybackAcceptedChange(
    val subjectId: Int,
    val episodeId: Int,
    val revision: Long,
)
