/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.datasources.bangumi.apis.DefaultApi
import me.him188.ani.datasources.bangumi.infrastructure.HttpResponse
import me.him188.ani.datasources.bangumi.next.apis.EpisodeBangumiNextApi
import me.him188.ani.datasources.bangumi.next.apis.SubjectBangumiNextApi
import me.him188.ani.datasources.bangumi.next.infrastructure.HttpResponse as NextHttpResponse
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UnsafeScopedHttpClientApi

/**
 * 官方 Bangumi API 的單一入口。
 *
 * 產生的 client 只在一次 request 期間借用 HTTP client，不把 platform client 或 token 帶出作用域。
 * 公開資料以匿名請求為主；需要帳號的 endpoint 會自動使用本機保存的 Bangumi access token。
 */
class BangumiApiProvider(
    private val client: ScopedHttpClient,
    private val tokenRepository: TokenRepository,
) {
    suspend fun hasAccessToken(): Boolean = currentAccessToken() != null

    suspend fun <T : Any> request(block: suspend DefaultApi.() -> HttpResponse<T>): T {
        return withApi { block(this).body() }
    }

    suspend fun <T : Any> nextSubjectRequest(block: suspend SubjectBangumiNextApi.() -> NextHttpResponse<T>): T {
        return withHttpClient { httpClient ->
            val api = SubjectBangumiNextApi(
                baseUrl = BANGUMI_API_BASE_URL,
                httpClient = httpClient,
            )
            currentAccessToken()?.let(api::setBearerToken)
            block(api).body()
        }
    }

    suspend fun <T : Any> nextEpisodeRequest(block: suspend EpisodeBangumiNextApi.() -> NextHttpResponse<T>): T {
        return withHttpClient { httpClient ->
            val api = EpisodeBangumiNextApi(
                baseUrl = BANGUMI_API_BASE_URL,
                httpClient = httpClient,
            )
            currentAccessToken()?.let(api::setBearerToken)
            block(api).body()
        }
    }

    suspend fun deleteUserCollection(subjectId: Int) {
        withHttpClient { httpClient ->
            val response = httpClient.delete("$BANGUMI_API_BASE_URL/v0/users/-/collections/$subjectId") {
                currentAccessToken()?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            check(response.status.isSuccess() || response.status == HttpStatusCode.NotFound) {
                "Bangumi collection delete failed: ${response.status}"
            }
        }
    }

    private suspend fun <T> withApi(block: suspend DefaultApi.() -> T): T {
        return withHttpClient { httpClient ->
            val api = DefaultApi(
                baseUrl = BANGUMI_API_BASE_URL,
                httpClient = httpClient,
            )
            currentAccessToken()?.let(api::setBearerToken)
            block(api)
        }
    }

    @OptIn(UnsafeScopedHttpClientApi::class)
    private suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val ticket = client.borrow()
        try {
            return block(ticket.client)
        } finally {
            client.returnClient(ticket)
        }
    }

    private suspend fun currentAccessToken(): String? = tokenRepository.session.first()
        .let { it as? AccessTokenSession }
        ?.tokens
        ?.bangumiAccessToken
        ?.takeIf(String::isNotBlank)

    private companion object {
        const val BANGUMI_API_BASE_URL = "https://api.bgm.tv"
    }
}
