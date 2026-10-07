package com.wynime.utils.ktor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.plugin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import com.wynime.utils.ktor.HttpLogger.logHttp
import com.wynime.utils.logging.Logger
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.measureTimedValue

expect fun getPlatformKtorEngine(): HttpClientEngineFactory<*>

fun createDefaultHttpClient(
    clientConfig: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(getPlatformKtorEngine()) {
    install(HttpRequestRetry) {
        maxRetries = 1
        delayMillis { 1000 }
        retryIf { cause, response ->

            cause is IOException
        }
    }
    install(HttpCookies)
    install(HttpTimeout) {
        requestTimeoutMillis = 300_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    BrowserUserAgent()
    install(ContentNegotiation) {
        val xmlConverter = getXmlConverter()
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            },
        )
        register(ContentType.Text.Html, xmlConverter)
        register(ContentType.Text.Xml, xmlConverter)
    }
    followRedirects = true
    install(HttpRedirect) {
        checkHttpMethod = false
        allowHttpsDowngrade = true
    }
    expectSuccess = true
    clientConfig()
}

fun HttpClient.registerLogging(
    logger: Logger = logger("ktor"),
) {
    plugin(HttpSend).intercept { request ->
        val (result, duration) = measureTimedValue {
            kotlin.runCatching { execute(request) }
        }

        logger.logHttp(
            method = request.method,
            url = request.url.toString(),
            isAuthorized = request.headers.contains(HttpHeaders.Authorization),
            responseStatus = result.map { it.response.status },
            duration = duration,
        )
        result.getOrThrow()
    }
}

object HttpLogger {
    fun Logger.logHttp(
        method: HttpMethod,
        url: String,
        isAuthorized: Boolean,
        responseStatus: Result<HttpStatusCode>,
        duration: Duration,
    ) {
        when {

            responseStatus.isFailure -> {
                if (responseStatus.exceptionOrNull() is CancellationException) {
                    warn { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }
                } else {
                    error { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }
                }
            }

            responseStatus.getOrNull()?.isSuccess() == true ->
                info { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }

            else -> warn { buildHttpRequestLog(method, url, isAuthorized, responseStatus, duration) }
        }
    }

    private fun buildHttpRequestLog(
        method: HttpMethod,
        url: String,
        isAuthorized: Boolean,
        responseStatus: Result<HttpStatusCode>,
        duration: Duration,
    ): String {
        val methodStr = method.value.padStart(5, ' ')
        return buildString {
            append(methodStr)
            append(" ")
            append(url)
            append(" ")
            if (isAuthorized) {
                append("[Authorized]")
            }

            append(": ")

            responseStatus.fold(
                onSuccess = {
                    append(it.toString())
                },
                onFailure = {
                    when (it) {
                        is CancellationException -> append("CANCELLED")
                        is IOException -> append("IO_EXCEPTION")
                        else -> append("FAILED")
                    }
                },
            )

            append(" in ")
            append(duration.toString())
        }
    }
}

internal expect fun getXmlConverter(): ContentConverter
