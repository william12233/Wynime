/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import io.ktor.client.request.request
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.headers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaDetector
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.source.plugin.api.SourceResultStatus
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.source.plugin.api.SourceHttpClient
import me.him188.ani.source.plugin.api.SourceHttpRequest
import me.him188.ani.source.plugin.api.SourceHttpResponse
import me.him188.ani.source.plugin.api.SourcePlugin
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.source.plugin.api.SourcePluginLogger
import me.him188.ani.source.plugin.api.SourcePluginPlatform
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.logging.Logger
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.ktor.ScopedHttpClient
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

data class LoadedSourcePlugin(
    val plugin: SourcePlugin,
    private val releaseClassLoader: () -> Unit,
) : AutoCloseable {
    override fun close() {
        try {
            plugin.close()
        } finally {
            releaseClassLoader()
        }
    }
}

interface SourcePluginLoader {
    fun load(
        artifact: SystemPath,
        entryClass: String,
        context: SourcePluginContext,
    ): LoadedSourcePlugin
}

expect fun createSourcePluginLoader(context: Context): SourcePluginLoader

/** Creates one stable HTTP/cookie session per loaded plugin instance. */
class SourcePluginContextFactory(
    private val httpClientProvider: HttpClientProvider,
    private val platform: SourcePluginPlatform,
    private val hostVersion: String = currentAniBuildConfig.versionName,
    private val webSessionManager: WebSessionManager? = null,
    private val cookieJar: WebSourceCookieJar = WebSourceCookieJar(),
    private val identityRegistry: WebSourceIdentityRegistry = WebSourceIdentityRegistry(),
) {
    fun create(pluginId: String): SourcePluginContext {
        val scopedClient = httpClientProvider.get(
            userAgent = ScopedHttpClientUserAgent.BROWSER,
            cookieJar = cookieJar,
            identityRegistry = identityRegistry,
        )
        val pluginLogger = SourcePluginLoggerAdapter(logger("source-plugin/$pluginId"))
        return DefaultSourcePluginContext(
            pluginId = pluginId,
            hostVersion = hostVersion,
            platform = platform,
            http = SourcePluginHttpClient(
                client = scopedClient,
                pluginId = pluginId,
                webSessionManager = webSessionManager,
                pluginLogger = pluginLogger,
            ),
            logger = pluginLogger,
        )
    }
}

private data class DefaultSourcePluginContext(
    override val pluginId: String,
    override val hostVersion: String,
    override val platform: SourcePluginPlatform,
    override val http: SourceHttpClient,
    override val logger: SourcePluginLogger,
) : SourcePluginContext

class SourcePluginHttpClient(
    private val client: ScopedHttpClient,
    private val pluginId: String? = null,
    private val webSessionManager: WebSessionManager? = null,
    private val pluginLogger: SourcePluginLogger? = null,
) : SourceHttpClient {
    private val logger = logger<SourcePluginHttpClient>()

    override suspend fun execute(pluginRequest: SourceHttpRequest): SourceHttpResponse = try {
        withTimeout(15.seconds) {
            executeWithRetry(pluginRequest)
        }
    } catch (error: TimeoutCancellationException) {
        throw SourcePluginFailure(
            status = SourceResultStatus.TIMEOUT,
            diagnostics = sourceFailureDiagnostics(
                traceId = pluginRequest.traceId,
                provider = pluginId ?: "host",
                entryPoint = pluginRequest.entryPoint,
                status = SourceResultStatus.TIMEOUT,
                url = pluginRequest.url,
                refererPresent = pluginRequest.headers.keys.any { it.equals("Referer", ignoreCase = true) },
                failureReason = "request timeout",
            ),
            retryable = true,
            cause = error,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: SourcePluginFailure) {
        throw error
    } catch (error: Throwable) {
        throw SourcePluginFailure(
            status = SourceResultStatus.NETWORK_ERROR,
            diagnostics = sourceFailureDiagnostics(
                traceId = pluginRequest.traceId,
                provider = pluginId ?: "host",
                entryPoint = pluginRequest.entryPoint,
                status = SourceResultStatus.NETWORK_ERROR,
                url = pluginRequest.url,
                refererPresent = pluginRequest.headers.keys.any { it.equals("Referer", ignoreCase = true) },
                failureReason = error::class.simpleName,
            ),
            retryable = true,
            cause = error,
        )
    }

    private suspend fun executeWithRetry(pluginRequest: SourceHttpRequest): SourceHttpResponse {
        var retryCount = 0
        while (true) {
            try {
                return executeWithChallengeHandling(pluginRequest)
            } catch (error: SourcePluginFailure) {
                if (!error.retryable || retryCount >= MAX_RETRY_COUNT) throw error
                retryCount++
                delay(RETRY_DELAY_MILLIS)
            }
        }
    }

    private suspend fun executeWithChallengeHandling(pluginRequest: SourceHttpRequest): SourceHttpResponse {
        val response = executeOnce(pluginRequest)
        val sessionManager = webSessionManager
        val challengeKind = WebCaptchaDetector.detect(
            response.finalUrl.ifBlank { pluginRequest.url },
            response.bodyAsText(),
        )
        if (challengeKind == null) {
            return validatePluginResponse(pluginRequest, response)
        }

        // Repository downloads use the same client type but have no provider identity and must
        // retain their existing response handling. Provider traffic never hands a challenge page
        // to a plugin parser.
        if (pluginId == null) {
            return response
        }

        if (sessionManager == null) {
            throw failure(
                request = pluginRequest,
                response = response,
                status = SourceResultStatus.BLOCKED_BY_CHALLENGE,
                retryable = false,
                requiresVerification = true,
                challengeKind = challengeKind.name,
                reason = "shared web session is unavailable",
            )
        }

        pluginLogger?.info("偵測到來源網站驗證，準備使用互動網頁工作階段處理")
        val outcome = runCatching {
            val request = SolveRequest(
                mediaSourceId = pluginId.orEmpty(),
                pageUrl = response.finalUrl.ifBlank { pluginRequest.url },
                kind = challengeKind,
                expectation = PageExpectation.AnyContent,
            )
            val automatic = sessionManager.solve(request, interactive = false)
            if (automatic == SolveOutcome.Solved || !sessionManager.isInteractiveSupported) {
                automatic
            } else {
                sessionManager.solve(request, interactive = true)
            }
        }.getOrElse { error ->
            pluginLogger?.warn("來源網站驗證流程失敗: ${error::class.simpleName}")
            SolveOutcome.Failed(null)
        }
        if (outcome != SolveOutcome.Solved) {
            throw failure(
                request = pluginRequest,
                response = response,
                status = SourceResultStatus.BLOCKED_BY_CHALLENGE,
                retryable = false,
                requiresVerification = true,
                challengeKind = challengeKind.name,
                reason = "web session verification was not completed",
            )
        }

        // 驗證成功後只重試原請求一次；cookie 與 UA 已由 WebSessionManager 同步到共用 HTTP 工作階段。
        val retried = executeOnce(pluginRequest)
        val retryChallenge = WebCaptchaDetector.detect(
            retried.finalUrl.ifBlank { pluginRequest.url },
            retried.bodyAsText(),
        )
        if (retryChallenge != null) {
            throw failure(
                request = pluginRequest,
                response = retried,
                status = SourceResultStatus.BLOCKED_BY_CHALLENGE,
                retryable = false,
                requiresVerification = true,
                challengeKind = retryChallenge.name,
                reason = "challenge remained after the verified retry",
            )
        }
        return validatePluginResponse(pluginRequest, retried)
    }

    private fun validatePluginResponse(
        request: SourceHttpRequest,
        response: SourceHttpResponse,
    ): SourceHttpResponse {
        if (pluginId == null || response.statusCode in 200..399) return response

        val status = if (response.statusCode == 401 || response.statusCode == 407) {
            SourceResultStatus.AUTH_REQUIRED
        } else {
            SourceResultStatus.HTTP_ERROR
        }
        throw failure(
            request = request,
            response = response,
            status = status,
            retryable = response.statusCode == 408 || response.statusCode == 425 ||
                response.statusCode == 429 || response.statusCode >= 500,
            requiresVerification = status == SourceResultStatus.AUTH_REQUIRED,
            reason = "HTTP ${response.statusCode}",
        )
    }

    private fun failure(
        request: SourceHttpRequest,
        response: SourceHttpResponse?,
        status: SourceResultStatus,
        retryable: Boolean,
        requiresVerification: Boolean = false,
        challengeKind: String? = null,
        reason: String? = null,
    ): SourcePluginFailure = SourcePluginFailure(
        status = status,
        diagnostics = sourceFailureDiagnostics(
            traceId = request.traceId,
            provider = pluginId ?: "host",
            entryPoint = request.entryPoint,
            status = status,
            url = response?.finalUrl?.ifBlank { request.url } ?: request.url,
            statusCode = response?.statusCode,
            contentType = response?.contentType,
            redirectCount = response?.redirectCount,
            elapsedMillis = response?.elapsedMillis,
            refererPresent = request.headers.keys.any { it.equals("Referer", ignoreCase = true) },
            challengeDetected = challengeKind,
            failureReason = reason,
        ),
        retryable = retryable,
        requiresVerification = requiresVerification,
    )

    private suspend fun executeOnce(pluginRequest: SourceHttpRequest): SourceHttpResponse {
        val started = TimeSource.Monotonic.markNow()
        return client.use {
            val response = request(pluginRequest.url) {
                method = HttpMethod(pluginRequest.method)
                expectSuccess = false
                pluginRequest.headers.forEach { (name, value) -> header(name, value) }
                pluginRequest.body?.let(::setBody)
            }
            val responseHeaders = response.headers.entries().associate { (name, values) ->
                name to values.joinToString(",")
            }
            val finalUrl = response.call.request.url.toString()
            SourceHttpResponse(
                statusCode = response.status.value,
                finalUrl = finalUrl,
                headers = responseHeaders,
                body = response.bodyAsBytes(),
                elapsedMillis = started.elapsedNow().inWholeMilliseconds,
                redirectCount = if (finalUrl == pluginRequest.url) 0 else 1,
                contentType = responseHeaders.entries
                    .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value,
            )
        }
    }

    private companion object {
        const val MAX_RETRY_COUNT = 1
        const val RETRY_DELAY_MILLIS = 250L
    }
}

private class SourcePluginLoggerAdapter(
    private val delegate: Logger,
) : SourcePluginLogger {
    override fun debug(message: String) = delegate.debug(message)
    override fun info(message: String) = delegate.info(message)
    override fun warn(message: String, throwable: Throwable?) = delegate.warn(message, throwable)
    override fun error(message: String, throwable: Throwable?) = delegate.error(message, throwable)
}
