package com.wynime.app.domain.settings

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.data.models.preference.ProxyMode
import com.wynime.app.data.models.preference.ProxySettings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.fetch.toClientProxyConfig
import com.wynime.app.platform.SystemProxyDetector
import com.wynime.utils.ktor.setProxy
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import kotlin.time.Duration.Companion.hours

interface ProxyProvider {
    val proxy: Flow<ProxyConfig?>
}

suspend fun ProxyProvider.collectProxyTo(client: HttpClient) {
    proxy.collect {
        client.engineConfig.setProxy(it?.toClientProxyConfig())
    }
}

data object NoProxyProvider : ProxyProvider {
    override val proxy: Flow<ProxyConfig?> = flowOf(null)
}

data class ConstantProxyProvider(
    val value: ProxyConfig?,
) : ProxyProvider {
    override val proxy: Flow<ProxyConfig?> = flowOf(value)
}

data class FlowProxyProvider(
    override val proxy: Flow<ProxyConfig?>,
) : ProxyProvider

class SystemProxyProvider(
    backgroundScope: CoroutineScope,
) : ProxyProvider {
    private val logger = logger<SystemProxyDetector>()

    override val proxy: Flow<ProxyConfig?> = flow {
        while (currentCoroutineContext().isActive) {
            val defaultProxy = SystemProxyDetector.instance.detect()

            if (defaultProxy == null) {
                emit(null)
            } else {
                emit(ProxyConfig(url = defaultProxy.url.toString()))
            }

            delay(1.hours)
        }
    }.distinctUntilChanged()
        .onEach {
            logger.info { "Detected system proxy: $it" }
        }
        .shareIn(backgroundScope, started = SharingStarted.WhileSubscribed(), replay = 1)
}

class SettingsBasedProxyProvider(
    private val settingsRepository: SettingsRepository,
    backgroundScope: CoroutineScope,
) : ProxyProvider by ProxySettingsFlowProxyProvider(
    settingsRepository.proxySettings.flow, backgroundScope,
)

class ProxySettingsFlowProxyProvider(
    private val flow: Flow<ProxySettings>,
    backgroundScope: CoroutineScope,
) : ProxyProvider {
    override val proxy: Flow<ProxyConfig?> by lazy {
        flow.map { it.default }
            .distinctUntilChanged()
            .transformLatest { settings ->
                coroutineScope {
                    val provider = when (settings.mode) {
                        ProxyMode.DISABLED -> NoProxyProvider
                        ProxyMode.SYSTEM -> SystemProxyProvider(this)
                        ProxyMode.CUSTOM -> ConstantProxyProvider(settings.customConfig)
                    }

                    emitAll(provider.proxy)
                }
            }
            .shareIn(backgroundScope, started = SharingStarted.WhileSubscribed(), replay = 1)
    }
}