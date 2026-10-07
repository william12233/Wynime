package com.wynime.app.domain.media.fetch

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import com.wynime.app.data.models.preference.ProxyAuthorization
import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.data.repository.media.MediaSourceInstanceRepository
import com.wynime.app.data.repository.media.updateConfig
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.ScopedHttpClientUserAgent
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.media.download.MediaDownloadManager.Companion.LOCAL_FS_MEDIA_SOURCE_ID
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.mediasource.instance.MediaSourceInstance
import com.wynime.app.domain.mediasource.instance.MediaSourceSave
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import com.wynime.app.domain.settings.ProxyProvider
import com.wynime.app.platform.getWynimeUserAgent
import com.wynime.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import com.wynime.datasources.api.source.FactoryId
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceConfig
import com.wynime.datasources.api.source.MediaSourceFactory
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.serializeArguments
import com.wynime.datasources.ikaros.IkarosMediaSource
import com.wynime.datasources.jellyfin.EmbyMediaSource
import com.wynime.datasources.jellyfin.JellyfinMediaSource
import com.wynime.utils.coroutines.onReplacement
import com.wynime.utils.ktor.ClientProxyConfig
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.coroutines.CoroutineContext

interface MediaSourceManager {

    val allInstances: Flow<List<MediaSourceInstance>>

    val allFactories: List<MediaSourceFactory>

    val allFactoryIds: List<FactoryId>

    val allFactoryIdsExceptLocal: List<FactoryId>
        get() = allFactoryIds.filter { !isLocal(it) }

    val mediaFetcher: Flow<MediaFetcher>

    val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader

    fun isLocal(factoryId: FactoryId): Boolean = isLocal(factoryId.value)
    fun isLocal(mediaSourceId: String): Boolean {
        return mediaSourceId == LOCAL_FS_MEDIA_SOURCE_ID
    }

    fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?>

    fun findInfoByFactoryId(factoryId: FactoryId): MediaSourceInfo? {
        return allFactories.find { it.factoryId == factoryId }?.info
    }

    fun infoFlowByMediaSourceId(mediaSourceId: String): Flow<MediaSourceInfo?> {
        if (mediaSourceId == "Bangumi") {
            return flowOf(
                MediaSourceInfo(
                    "Bangumi",
                    "提供观看记录数据",
                    "https://bangumi.tv",
                    "https://bangumi.tv/img/favicon.ico",
                    iconResourceId = "bangumi.png",
                ),
            )
        }
        return allInstances.map { list ->
            list.find { it.mediaSourceId == mediaSourceId }?.source?.info
        }
    }

    suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    )

    suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave>

    suspend fun partiallyReorderInstances(instanceIds: List<String>)

    suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean
    suspend fun setEnabled(instanceId: String, enabled: Boolean)
    suspend fun removeInstance(instanceId: String)

    suspend fun setEnabled(instanceIds: Collection<String>, enabled: Boolean) {
        instanceIds.forEach { setEnabled(it, enabled) }
    }

    suspend fun removeInstances(instanceIds: Collection<String>) {
        instanceIds.forEach { removeInstance(it) }
    }

    fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers>
}

suspend fun <T> MediaSourceManager.addInstance(
    instanceId: String,
    mediaSourceId: String,
    factoryId: FactoryId,
    serializer: SerializationStrategy<T>,
    arguments: T
) = addInstance(
    instanceId,
    mediaSourceId,
    factoryId,
    MediaSourceConfig(serializedArguments = MediaSourceConfig.serializeArguments(serializer, arguments)),
)

suspend fun MediaSourceManager.updateMediaSourceArguments(
    instanceId: String,
    arguments: JsonElement
): Boolean {
    val config = instanceConfigFlow(instanceId).first() ?: return false
    return updateConfig(
        instanceId,
        config.copy(serializedArguments = arguments),
    )
}

suspend fun <T> MediaSourceManager.updateMediaSourceArguments(
    instanceId: String,
    serializer: SerializationStrategy<T>,
    arguments: T
) = updateMediaSourceArguments(instanceId, MediaSourceConfig.serializeArguments(serializer, arguments))

suspend fun MediaSourceManager.createFetchFetchSession(requestLazy: Flow<MediaFetchRequest>): MediaFetchSession =
    mediaFetcher.first().newSession(requestLazy)

class MediaSourceManagerImpl(

    additionalSources: () -> List<MediaSource>,
    private val pluginSources: Flow<List<MediaSource>> = flowOf(emptyList()),
    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,
) : MediaSourceManager, KoinComponent {
    private val proxyProvider: ProxyProvider by inject()
    private val instances: MediaSourceInstanceRepository by inject()
    private val webSourceCookieJar: WebSourceCookieJar by inject()
    private val webSourceIdentityRegistry: WebSourceIdentityRegistry by inject()
    private val clientProvider: HttpClientProvider by inject()

    private val scope = CoroutineScope(
        CoroutineExceptionHandler { _, throwable ->

            logger.error(throwable) { "DownloadProviderManager scope error" }
        },
    )
    private val factories: List<MediaSourceFactory> = buildSet {
        add(JellyfinMediaSource.Factory())
        add(EmbyMediaSource.Factory())
        add(IkarosMediaSource.Factory())
    }.toList()

    private val additionalSources by lazy {
        additionalSources().map { source ->
            MediaSourceInstance(
                instanceId = source.mediaSourceId,
                factoryId = FactoryId(source.mediaSourceId),
                isEnabled = true,
                config = MediaSourceConfig.Default,
                source = source,
            )
        }
    }
    override val allInstances =
        combine(instances.flow, proxyProvider.proxy.distinctUntilChanged(), pluginSources) { saves, config, plugins ->

            this.additionalSources + plugins.map { plugin ->
                MediaSourceInstance(
                    instanceId = plugin.mediaSourceId,
                    factoryId = FactoryId(plugin.mediaSourceId),
                    isEnabled = true,
                    config = MediaSourceConfig.Default,
                    source = plugin,
                )
            } + saves.mapNotNull { createInstance(it, config) }
        }.onReplacement { list ->
            list.forEach { it.close() }
        }.flowOn(flowCoroutineContext).shareIn(scope, replay = 1, started = SharingStarted.Lazily)
    override val allFactories: List<MediaSourceFactory> get() = factories

    private fun createInstance(save: MediaSourceSave, config: ProxyConfig?): MediaSourceInstance? {
        val factory = factories.find { it.factoryId == save.factoryId }
        return if (factory == null) {
            logger.error { "MediaSourceFactory '${save.factoryId}' not found for ${save.mediaSourceId}" }
            null
        } else {
            MediaSourceInstance(
                instanceId = save.instanceId,
                factoryId = save.factoryId,
                isEnabled = save.isEnabled,
                config = save.config,
                source = factory.create(
                    config,
                    save.mediaSourceId,
                    save.config,

                    clientProvider.get(
                        ScopedHttpClientUserAgent.BROWSER,
                        cookieJar = webSourceCookieJar,
                        identityRegistry = webSourceIdentityRegistry,
                    ),
                ),
            )
        }
    }

    override val allFactoryIds: List<FactoryId> by lazy {
        factories
            .map { it.factoryId }
            .plus(this.additionalSources.map { it.factoryId })
    }

    override val mediaFetcher: Flow<MediaFetcher> = allInstances.map { instances ->
        MediaSourceMediaFetcher(
            configProvider = { MediaFetcherConfig() },
            mediaSources = instances,
        )
    }
    override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(
        allInstances.map { list -> list.map { it.source } },
    )

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> {
        return instances.flow.map { list ->
            list.find { it.instanceId == instanceId }?.config
        }
    }

    override suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    ) {
        val save = MediaSourceSave(
            instanceId = instanceId,
            mediaSourceId = mediaSourceId,
            factoryId = factoryId,
            isEnabled = true,
            config = config,
        )
        instances.add(save)
    }

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> {
        return instances.flow.first().filter { save ->
            save.config.subscriptionId == subscriptionId
        }
    }

    override suspend fun partiallyReorderInstances(instanceIds: List<String>) {
        return instances.partiallyReorder(instanceIds)
    }

    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
        return instances.updateConfig(instanceId, config)
    }

    override suspend fun setEnabled(instanceId: String, enabled: Boolean) {
        instances.updateSave(instanceId) {
            copy(isEnabled = enabled)
        }
    }

    override suspend fun removeInstance(instanceId: String) {
        instances.remove(instanceId)
    }

    override suspend fun setEnabled(instanceIds: Collection<String>, enabled: Boolean) {
        instances.updateSaves(instanceIds) {
            copy(isEnabled = enabled)
        }
    }

    override suspend fun removeInstances(instanceIds: Collection<String>) {
        instances.removeAll(instanceIds)
    }

    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)

    private fun MediaSourceFactory.create(
        proxyConfig: ProxyConfig?,
        mediaSourceId: String,
        config: MediaSourceConfig,
        client: ScopedHttpClient,
    ): MediaSource {
        @Suppress("DEPRECATION")
        val mediaSourceConfig = config.copy(
            proxy = config.proxy ?: proxyConfig?.toClientProxyConfig(),
            userAgent = getWynimeUserAgent(),
        )
        return create(mediaSourceId, mediaSourceConfig, client)
    }

    private companion object {
        private val logger = logger<MediaSourceManager>()
    }
}

fun ProxyConfig.toClientProxyConfig() = ClientProxyConfig(
    url = url,
    authorization = authorization?.toHeader(),
)

@OptIn(ExperimentalEncodingApi::class)
fun ProxyAuthorization.toHeader(): String = "Basic ${Base64.encode("$username:$password".encodeToByteArray())}"
