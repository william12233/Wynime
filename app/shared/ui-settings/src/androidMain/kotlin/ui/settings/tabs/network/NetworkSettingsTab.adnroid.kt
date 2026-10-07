package com.wynime.app.ui.settings.tabs.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.domain.media.fetch.MediaFetcher
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.mediasource.instance.MediaSourceInstance
import com.wynime.app.domain.mediasource.instance.MediaSourceSave
import com.wynime.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import com.wynime.datasources.api.source.FactoryId
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceConfig
import com.wynime.datasources.api.source.MediaSourceFactory
import com.wynime.datasources.api.source.TestHttpMediaSource
import com.wynime.utils.platform.annotations.TestOnly

@TestOnly
fun createTestMediaSourceInstance(
    instanceId: String,
    factoryId: FactoryId,
    isEnabled: Boolean,
    config: MediaSourceConfig,
    source: MediaSource,
): MediaSourceInstance = MediaSourceInstance(
    instanceId = instanceId,
    factoryId = factoryId,
    isEnabled = isEnabled,
    config = config,
    source = source,
)

@TestOnly
fun createTestMediaSourceManager() = object : MediaSourceManager {
    override val allInstances = MutableStateFlow(
        listOf(
            createTestMediaSourceInstance(
                "1",
                FactoryId("web-test"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("acg.rip", randomConnectivity = true),
            ),

            createTestMediaSourceInstance(
                "1",
                FactoryId("web-test"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("source-b", randomConnectivity = true),
            ),

            createTestMediaSourceInstance(
                "1",
                FactoryId("web-test"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("source-c", randomConnectivity = true),
            ),

            createTestMediaSourceInstance(
                "1",
                FactoryId("web-test"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("local", randomConnectivity = true),
            ),
        ),
    )
    override val allFactories: List<MediaSourceFactory> = emptyList()
    override val allFactoryIds: List<FactoryId> = allInstances.value.map { it.factoryId }
    override val allFactoryIdsExceptLocal: List<FactoryId>
        get() = allFactoryIds.filter { !isLocal(it) }
    override val mediaFetcher: Flow<MediaFetcher> get() = flowOf()
    override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader =
        MediaSourceWebVideoMatcherLoader(flowOf(emptyList()))

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> {
        return MutableStateFlow(MediaSourceConfig())
    }

    override suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    ) {
    }

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> {
        return emptyList()
    }

    override suspend fun partiallyReorderInstances(instanceIds: List<String>) {
    }

    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
        return false
    }

    override suspend fun setEnabled(instanceId: String, enabled: Boolean) {
    }

    override suspend fun removeInstance(instanceId: String) {
    }

    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> {
        return flowOf(MediaSelectorSourceTiers.Empty)
    }
}

