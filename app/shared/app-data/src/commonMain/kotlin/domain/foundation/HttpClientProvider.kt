package com.wynime.app.domain.foundation

import io.ktor.client.HttpClient
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.domain.media.fetch.toClientProxyConfig
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import com.wynime.app.domain.settings.ProxyProvider
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.ktor.UnsafeScopedHttpClientApi
import com.wynime.utils.ktor.createDefaultHttpClient
import com.wynime.utils.ktor.proxy
import com.wynime.utils.ktor.registerLogging
import com.wynime.utils.ktor.setProxy
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.annotations.TestOnly

sealed class HttpClientProvider {

    abstract val configurationFlow: Flow<*>

    abstract fun get(
        features: Set<ScopedHttpClientFeatureKeyValue<*>>,
    ): ScopedHttpClient

}

fun HttpClientProvider.get(
    userAgent: ScopedHttpClientUserAgent = ScopedHttpClientUserAgent.WYNIME,
    serverListConfig: ServerListFeatureConfig = ServerListFeatureConfig(serviceServerRules = null),
    useSse: Boolean = false,
    distroChannel: String? = currentWynimeBuildConfig.distroChannel,
    cookieJar: WebSourceCookieJar? = null,
    identityRegistry: WebSourceIdentityRegistry? = null,
): ScopedHttpClient = get(
    buildSet {
        add(UserAgentFeature.withValue(userAgent))
        add(ServerListFeature.withValue(serverListConfig))
        add(ConvertSendCountExceedExceptionFeature.withValue(true))
        add(SseFeature.withValue(useSse))
        add(VersionExpiryFeature.withValue(false))
        add(DistributionChannelFeature.withValue { distroChannel })
        if (cookieJar != null) add(CookieJarFeature.withValue(cookieJar))
        if (identityRegistry != null) add(WebSourceIdentityFeature.withValue(identityRegistry))
    },
)

class DefaultHttpClientProvider(
    private val proxyProvider: ProxyProvider,
    private val backgroundScope: CoroutineScope,
    featureHandlers: List<ScopedHttpClientFeatureHandler<*>> = listOf(UserAgentFeatureHandler),
) : HttpClientProvider() {

    private data class Matrix(
        val features: Set<ScopedHttpClientFeatureKeyValue<*>>,
        val proxyConfig: ProxyConfig?,
    )

    data class HoldingInstanceMatrix(
        val features: Set<ScopedHttpClientFeatureKeyValue<*>>,
    )

    @Suppress("UNCHECKED_CAST")
    private val featureHandlers: Map<ScopedHttpClientFeatureKey<Any?>, ScopedHttpClientFeatureHandler<Any?>> =
        featureHandlers.associateBy { it.key } as Map<ScopedHttpClientFeatureKey<Any?>, ScopedHttpClientFeatureHandler<Any?>>

    private val clientLogger = logger<HttpClientProvider>()

    private val pool = ReuseObjectPool<Matrix, HttpClient>(
        newInstance = { createClient(it.features, it.proxyConfig) },
        onRelease = { it.close() },
    )

    private val proxyListeningStarted = atomic(false)
    private var proxyListeningJob: Job? = null

    private val currentProxyConfig = MutableStateFlow<ProxyConfig?>(null)
    override val configurationFlow: Flow<*> get() = currentProxyConfig

    @TestOnly
    fun getProxyListeningStarted(): Boolean = proxyListeningStarted.value

    @TestOnly
    fun getCurrentProxyConfig(): ProxyConfig? = currentProxyConfig.value

    private fun createClient(
        features: Set<ScopedHttpClientFeatureKeyValue<*>>,
        proxyConfig: ProxyConfig?,
    ): HttpClient {
        return createDefaultHttpClient {
            for (feature in features) {
                val handler = featureHandlers[feature.key]
                    ?: error("No handler for feature ${feature.key}")
                val value = feature.value
                if (value != FEATURE_NOT_SET) {
                    handler.applyToConfig(this, value)
                }
            }
            proxy(proxyConfig?.toClientProxyConfig())
        }.apply {
            registerLogging(clientLogger)

            for (feature in features) {
                val handler = featureHandlers[feature.key]
                    ?: error("No handler for feature ${feature.key}")
                val value = feature.value
                if (value != FEATURE_NOT_SET) {
                    handler.applyToClient(this, value)
                }
            }
        }
    }

    suspend fun startProxyListening(
        holdReferences: Sequence<HoldingInstanceMatrix>,
    ) {
        if (!proxyListeningStarted.compareAndSet(expect = false, update = true)) {
            error("Proxy listening already started")
        }

        val flowScope =
            backgroundScope.coroutineContext.childScope(CoroutineName("HttpClientProvider.ProxyListening"))
        try {
            val proxyConfigFlow =
                proxyProvider.proxy.stateIn(flowScope)
            val firstValueReady = CompletableDeferred<Unit>()

            flowScope.launch {
                proxyConfigFlow.collectLatest {

                    currentProxyConfig.value = it
                    firstValueReady.complete(Unit)

                    coroutineScope {

                        for ((features) in holdReferences) {
                            launch {

                                get(features).use {
                                    this.engineConfig.setProxy(it?.toClientProxyConfig())
                                    awaitCancellation()
                                }
                            }
                        }
                    }
                }
            }
            firstValueReady.await()
        } catch (e: Throwable) {

            flowScope.cancel(
                CancellationException(
                    "Failed to start proxy listening, cancelling premature scope",
                    e,
                ),
            )
            throw e

        }
        proxyListeningJob = flowScope.coroutineContext.job
    }

    override fun get(features: Set<ScopedHttpClientFeatureKeyValue<*>>): ScopedHttpClient {
        return ScopedHttpClientImpl(features.extendWithNotSet())
    }

    private fun Set<ScopedHttpClientFeatureKeyValue<*>>.extendWithNotSet(): Set<ScopedHttpClientFeatureKeyValue<*>> {
        return featureHandlers.keys.mapTo(mutableSetOf()) { key ->
            this.find { it.key == key } ?: ScopedHttpClientFeatureKeyValue.createNotSet(key)
        }
    }

    private inner class ScopedHttpClientImpl(
        private val features: Set<ScopedHttpClientFeatureKeyValue<*>>,
    ) : ScopedHttpClient() {
        @UnsafeScopedHttpClientApi
        override fun borrow(): Ticket {
            val myMatrix = Matrix(features, currentProxyConfig.value)
            return TicketImpl(myMatrix, pool.borrow(myMatrix))
        }

        @UnsafeScopedHttpClientApi
        override fun returnClient(ticket: Ticket) {
            check(ticket is TicketImpl) { "Ticket must be an instance of TicketImpl. Do not implement the Ticket interface. Do not use delegation (`by`) keyword for this type." }
            return pool.release(ticket.matrix, ticket.client)
        }

        override fun toString(): String = "WrapperHttpClientImpl(features=$features)"
    }

    @UnsafeScopedHttpClientApi
    private data class TicketImpl(
        val matrix: Matrix,
        override val client: HttpClient,
    ) : ScopedHttpClient.Ticket

    @TestOnly
    suspend fun forceReleaseAll() {

        proxyListeningJob?.cancelAndJoin()
        proxyListeningJob = null
        @OptIn(TestOnly::class)
        pool.forceReleaseAll()
    }
}

