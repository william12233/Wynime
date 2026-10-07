package com.wynime.app.domain.mediasource.instance

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.datasources.api.source.FactoryId
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceConfig
import com.wynime.utils.platform.Uuid
import com.wynime.utils.platform.annotations.SerializationOnly
import com.wynime.utils.platform.annotations.TestOnly

@Stable
class MediaSourceInstance(
    val instanceId: String,
    val factoryId: FactoryId,
    val isEnabled: Boolean,
    val config: MediaSourceConfig,
    val source: MediaSource,
) : AutoCloseable {
    override fun close() {
        source.close()
    }

    val mediaSourceId: String get() = source.mediaSourceId
}

@Serializable
data class MediaSourceSave @SerializationOnly constructor(
    val instanceId: String,
    val mediaSourceId: String,
    val factoryId: FactoryId = FactoryId(mediaSourceId),
    val isEnabled: Boolean,
    val config: MediaSourceConfig,
    @Transient private val _primaryConstructorMarker: Int = 0,
) {
    @OptIn(SerializationOnly::class)
    constructor(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        isEnabled: Boolean,
        config: MediaSourceConfig,
    ) : this(instanceId, mediaSourceId, factoryId, isEnabled, config, 0)
}

@TestOnly
fun createTestMediaSourceInstance(
    source: MediaSource,
    instanceId: String = Uuid.randomString(),
    mediaSourceId: String = source.mediaSourceId,
    isEnabled: Boolean = true,
    config: MediaSourceConfig = MediaSourceConfig.Default,
    factoryId: FactoryId = FactoryId(mediaSourceId),
): MediaSourceInstance {
    return MediaSourceInstance(
        instanceId = instanceId,
        factoryId = factoryId,
        isEnabled = isEnabled,
        config = config,
        source = source,
    )
}
