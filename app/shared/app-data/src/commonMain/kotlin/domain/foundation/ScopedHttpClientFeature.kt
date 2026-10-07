package com.wynime.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import com.wynime.app.platform.getWynimeUserAgent
import com.wynime.utils.coroutines.Symbol
import com.wynime.utils.ktor.userAgent
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField

data class ScopedHttpClientFeatureKey<V>(

    val id: String,
)

abstract class ScopedHttpClientFeatureHandler<V>(
    val key: ScopedHttpClientFeatureKey<V>,
) {

    open fun applyToConfig(config: HttpClientConfig<*>, value: V) {}

    open fun applyToClient(client: HttpClient, value: V) {}
}

fun <T> ScopedHttpClientFeatureKey<T>.withValue(value: T): ScopedHttpClientFeatureKeyValue<T> =
    ScopedHttpClientFeatureKeyValue.create(this, value)

@ConsistentCopyVisibility
data class ScopedHttpClientFeatureKeyValue<V> private constructor(
    val key: ScopedHttpClientFeatureKey<V>,
    internal val value: Any?,
) {
    companion object {

        fun <V> create(
            key: ScopedHttpClientFeatureKey<V>,
            value: V,
        ): ScopedHttpClientFeatureKeyValue<V> {
            require(value != FEATURE_NOT_SET) { "Value must not be FEATURE_NOT_SET" }
            return ScopedHttpClientFeatureKeyValue(key, value)
        }

        fun <V> createNotSet(key: ScopedHttpClientFeatureKey<V>): ScopedHttpClientFeatureKeyValue<V> {
            return ScopedHttpClientFeatureKeyValue(key, FEATURE_NOT_SET)
        }
    }
}

@JvmField
internal val FEATURE_NOT_SET = Symbol("NOT_REQUESTED")

val UserAgentFeature = ScopedHttpClientFeatureKey<ScopedHttpClientUserAgent>("UserAgent")

object UserAgentFeatureHandler :
    ScopedHttpClientFeatureHandler<ScopedHttpClientUserAgent>(UserAgentFeature) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: ScopedHttpClientUserAgent) {
        when (value) {
            ScopedHttpClientUserAgent.WYNIME -> config.userAgent(getWynimeUserAgent())
            ScopedHttpClientUserAgent.BROWSER -> config.BrowserUserAgent()
        }
    }
}

enum class ScopedHttpClientUserAgent {
    WYNIME,
    BROWSER
}

abstract class AbstractBearerTokenHandler(
    key: ScopedHttpClientFeatureKey<Boolean>,
    private val bearerToken: Flow<String?>,
    private val onRefresh: suspend () -> BearerTokens?,
) : ScopedHttpClientFeatureHandler<Boolean>(key) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: Boolean) {
        if (!value) return
        config.install(Auth) {
            bearer {
                loadTokens {
                    bearerToken.first()?.let {
                        BearerTokens(it, "")
                    }
                }

                refreshTokens {
                    onRefresh()
                }
            }
        }
    }

    override fun applyToClient(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            val originalCall = execute(request)
            if (originalCall.response.status.value !in 100..399) {
                execute(request)
            } else {
                originalCall
            }
        }
    }
}

typealias DistributionChannelProvider = () -> String?

abstract class AbstractDistributionChannelHandler(
    key: ScopedHttpClientFeatureKey<DistributionChannelProvider>,
    private val defaultProvider: () -> String,
) : ScopedHttpClientFeatureHandler<DistributionChannelProvider>(key) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: DistributionChannelProvider) {
        config.defaultRequest {
            headers.appendIfNameAbsent(HEADER_DISTRO_CHANNEL, value.invoke() ?: defaultProvider())
        }
    }

    companion object {
        const val HEADER_DISTRO_CHANNEL = "X-Ani-Distro-Channel"
    }
}

val DistributionChannelFeature = ScopedHttpClientFeatureKey<DistributionChannelProvider>("DistributionChannel")

class DistributionChannelFeatureHandler(defaultProvider: () -> String) :
    AbstractDistributionChannelHandler(DistributionChannelFeature, defaultProvider)

val ServerListFeature = ScopedHttpClientFeatureKey<ServerListFeatureConfig>("ServerList")

data class ServerListFeatureConfig(
    val serviceServerRules: WynimeServerRule?,
) {
    data class WynimeServerRule(

        val hostMatches: Set<String>,
    ) {
        init {
            require(hostMatches.isNotEmpty()) { "hostMatches must not be empty" }
        }
    }

    companion object {
        const val MAGIC_ANI_SERVER_HOST = "MAGIC_ANI_SERVER"
        const val MAGIC_ANI_SERVER = "https://$MAGIC_ANI_SERVER_HOST/"

        val Default = ServerListFeatureConfig(
            serviceServerRules = WynimeServerRule(
                hostMatches = setOf(MAGIC_ANI_SERVER_HOST),
            ),
        )
    }
}

data class ServerListFeatureHandler(
    private val wynimeServerUrls: Flow<List<Url>>,
) : ScopedHttpClientFeatureHandler<ServerListFeatureConfig>(ServerListFeature) {

    private data class SelectionState(val signature: Int, val index: Int)

    private val selectionState = kotlinx.atomicfu.atomic<SelectionState?>(null)
    override fun applyToClient(client: HttpClient, value: ServerListFeatureConfig) {
        client.plugin(HttpSend).intercept { request ->
            value.serviceServerRules?.let { rule ->
                handleWynimeRule(rule, request)
            }?.let {
                return@intercept it
            }

            execute(request)
        }
    }

    private suspend fun Sender.handleWynimeRule(
        rule: ServerListFeatureConfig.WynimeServerRule,
        request: HttpRequestBuilder
    ): HttpClientCall? {
        if (rule.hostMatches.isEmpty() || rule.hostMatches.none { request.url.host.startsWith(it) }) {
            return null
        }

        val urls = wynimeServerUrls.first()
        if (urls.isEmpty()) {
            error("No server URL to try for ani server request")
        }

        val signature = urls.joinToString("|") { it.toString() }.hashCode()

        val startIndex = selectionState.value
            .takeIf { it != null && it.signature == signature && it.index in urls.indices }
            ?.index ?: 0

        var lastCall: HttpClientCall? = null

        for (i in urls.indices) {
            val index = (startIndex + i) % urls.size
            val serverUrl = urls[index]
            replaceUrl(request.url, serverUrl)

            if (lastCall != null) {
                logger.debug { "Trying alternative server $serverUrl for request ${request.url}" }
            }
            val thisCall = try {
                execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClientRequestException) {

                throw e
            } catch (_: ResponseException) {
                continue
            } catch (_: IOException) {
                continue
            }
            lastCall = thisCall

            if (thisCall.response.status.value in 100..399) {

                selectionState.value = SelectionState(signature, index)
                return thisCall
            } else {

                continue
            }
        }

        return lastCall ?: throw IOException(
            "All servers failed for request ${request.url}. Tried: " +
                    "\n${urls.joinToString("\n")}",
        )
    }

    private val logger = logger<ServerListFeatureHandler>()

    internal companion object {

        internal fun replaceUrl(urlBuilder: URLBuilder, newServerUrl: Url) {

            urlBuilder.protocol = newServerUrl.protocol
            urlBuilder.host = newServerUrl.host
            urlBuilder.port = newServerUrl.port
            urlBuilder.encodedUser = newServerUrl.encodedUser
            urlBuilder.encodedPassword = newServerUrl.encodedPassword
        }
    }
}

val ConvertSendCountExceedExceptionFeature = ScopedHttpClientFeatureKey<Boolean>("ConvertSendCountExceedException")

data object ConvertSendCountExceedExceptionFeatureHandler : ScopedHttpClientFeatureHandler<Boolean>(
    ConvertSendCountExceedExceptionFeature,
) {
    override fun applyToClient(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            try {
                execute(request)
            } catch (e: SendCountExceedException) {
                throw IOException("Send count exceeded for url ${request.url}, see cause", e)
            }
        }
    }
}

