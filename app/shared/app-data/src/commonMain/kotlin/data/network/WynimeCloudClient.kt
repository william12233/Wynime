/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

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
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi

/**
 * Wynime Cloud 的最小 client。Worker 以 hash 索引短期 session/ticket，Bangumi grant
 * 只透過加密 response 回到本機，再交給現有 token store；播放紀錄則以複合身份 upsert。
 */
class WynimeCloudClient(
    private val client: ScopedHttpClient,
) {
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

    /**
     * 返回 null 表示授权尚未完成。Worker 使用 425 表示可重试的 pending 状态。
     */
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
