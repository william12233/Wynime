/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.data.network.WynimeCloudClient
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** 直接通过 Wynime Cloud 完成 Bangumi OAuth，不经过 Wynime Account API。 */
class WynimeBangumiOAuthClient(
    private val cloudClient: WynimeCloudClient,
    private val platform: Platform = currentPlatform(),
) : OAuthClient {
    override suspend fun getOAuthRegisterLink(requestId: String): String = start(requestId)

    override suspend fun getOAuthBindLink(requestId: String): String = start(requestId)

    override suspend fun getResult(requestId: String): OAuthResult? {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }

        val callback = OAuthCallbackRegistry.take(requestId)
        if (callback?.error != null) {
            throw RepositoryRequestError(callback.error)
        }

        val ticket = callback?.ticket ?: cloudClient.pollBangumiOAuthTicket(requestId) ?: return null
        return try {
            cloudClient.exchangeBangumiOAuthTicket(ticket).toOAuthResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Conflict -> throw RepositoryRequestError(e.response.bodyAsText(), cause = e)
                else -> throw RepositoryException.wrapOrThrowCancellation(e)
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private suspend fun start(requestId: String): String {
        require(requestId.isNotBlank()) { "requestId must not be blank or empty" }
        return try {
            cloudClient.startBangumiOAuth(
                state = requestId,
                mode = "login",
                platform = platform.name.lowercase(),
                arch = platform.arch.displayName.lowercase(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

class WynimeCloudSessionRefresher(
    private val cloudClient: WynimeCloudClient,
    private val clock: Clock = Clock.System,
) : SessionManager.SessionRefresher {
    override suspend fun refresh(refreshToken: String): OAuthResult {
        require(refreshToken.isNotBlank()) { "cloud session token must not be blank" }
        return try {
            cloudClient.refreshSession(refreshToken).toOAuthResult(clock)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

private fun me.him188.ani.app.data.network.WynimeSessionResponse.toOAuthResult(clock: Clock = Clock.System): OAuthResult {
    val expiresInSeconds = (expiresAtMillis - clock.now().toEpochMilliseconds())
        .coerceAtLeast(0)
        .milliseconds
        .inWholeSeconds
    return OAuthResult(
        tokens = AccessTokenPair(
            aniAccessToken = "",
            expiresAtMillis = expiresAtMillis,
            bangumiAccessToken = bangumiAccessToken,
        ),
        expiresInSeconds = expiresInSeconds,
        refreshToken = sessionToken,
    )
}
