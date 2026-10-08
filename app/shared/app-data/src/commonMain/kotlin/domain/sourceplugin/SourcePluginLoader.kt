package com.wynime.app.domain.sourceplugin

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
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.ScopedHttpClientUserAgent
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.mediasource.web.PageExpectation
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.app.domain.mediasource.web.WebCaptchaDetector
import com.wynime.app.domain.mediasource.web.WebCaptchaKind
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import com.wynime.source.plugin.api.SourceResultStatus
import com.wynime.app.platform.Context
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.utils.io.SystemPath
import com.wynime.utils.logging.Logger
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.ktor.ScopedHttpClient
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

class SourcePluginContextFactory(
    private val httpClientProvider: HttpClientProvider,
    private val platform: SourcePluginPlatform,
    private val hostVersion: String = currentWynimeBuildConfig.versionName,
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
    } catch (error: LinkageError) {
        throw sourcePluginBoundaryFailure(
            traceId = pluginRequest.traceId,
            provider = pluginId ?: "host",
            entryPoint = pluginRequest.entryPoint,
            fallbackStatus = SourceResultStatus.NETWORK_ERROR,
            error = error,
            url = pluginRequest.url,
            retryable = false,
        )
    } catch (error: ClassCastException) {
        throw sourcePluginBoundaryFailure(
            traceId = pluginRequest.traceId,
            provider = pluginId ?: "host",
            entryPoint = pluginRequest.entryPoint,
            fallbackStatus = SourceResultStatus.NETWORK_ERROR,
            error = error,
            url = pluginRequest.url,
            retryable = false,
        )
    } catch (error: Error) {
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
        val challengeKind = WebCaptchaDetector.detect(
            response.finalUrl.ifBlank { pluginRequest.url },
            response.bodyAsText(),
        )
        if (challengeKind == null) {
            return validatePluginResponse(pluginRequest, response)
        }

        if (pluginId == null) {
            return response
        }

        pluginLogger?.info("偵測到來源網站驗證，等待使用者點擊「來源需要驗證」")
        throw failure(
            request = pluginRequest,
            response = response,
            status = SourceResultStatus.BLOCKED_BY_CHALLENGE,
            retryable = false,
            requiresVerification = true,
            challengeKind = challengeKind.name,
            verificationKind = challengeKind,
            reason = "verification is required before the original request can be retried",
        )
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
        verificationKind: WebCaptchaKind? = null,
        reason: String? = null,
    ): SourcePluginFailure {
        val verificationPageUrl = response?.finalUrl?.ifBlank { request.url } ?: request.url
        return SourcePluginFailure(
            status = status,
            diagnostics = sourceFailureDiagnostics(
                traceId = request.traceId,
                provider = pluginId ?: "host",
                entryPoint = request.entryPoint,
                status = status,
                url = verificationPageUrl,
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
            verificationRequest = verificationKind?.let { kind ->
                SolveRequest(
                    mediaSourceId = pluginId.orEmpty(),
                    pageUrl = verificationPageUrl,
                    kind = kind,
                    expectation = PageExpectation.AnyContent,
                )
            },
        )
    }

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
