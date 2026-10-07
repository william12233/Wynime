package com.wynime.app.domain.session.auth

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import com.wynime.app.data.network.WynimeCloudClient
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.domain.session.AccessTokenPair
import com.wynime.app.domain.session.SessionManager
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatform
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

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
                redirectUri = if (platform is Platform.Android) {
                    ANDROID_CUSTOM_SCHEME_REDIRECT_URI
                } else {
                    null
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private companion object {
        const val ANDROID_CUSTOM_SCHEME_REDIRECT_URI = "ani://bangumi-oauth-callback"
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

private fun com.wynime.app.data.network.WynimeSessionResponse.toOAuthResult(clock: Clock = Clock.System): OAuthResult {
    val expiresInSeconds = (expiresAtMillis - clock.now().toEpochMilliseconds())
        .coerceAtLeast(0)
        .milliseconds
        .inWholeSeconds
    return OAuthResult(
        tokens = AccessTokenPair(
            legacyServiceAccessToken = "",
            expiresAtMillis = expiresAtMillis,
            bangumiAccessToken = bangumiAccessToken,
        ),
        expiresInSeconds = expiresInSeconds,
        refreshToken = sessionToken,
    )
}
