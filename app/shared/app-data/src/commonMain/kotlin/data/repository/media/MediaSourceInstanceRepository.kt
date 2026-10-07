package com.wynime.app.data.repository.media

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import com.wynime.app.data.repository.Repository
import com.wynime.app.domain.mediasource.instance.MediaSourceSave
import com.wynime.datasources.api.source.MediaSourceConfig
import com.wynime.utils.platform.collections.partiallyReorderBy

sealed class MediaSourceInstanceRepository : Repository() {
    abstract val flow: Flow<List<MediaSourceSave>>

    abstract suspend fun clear()
    abstract suspend fun remove(instanceId: String)

    abstract suspend fun removeAll(instanceIds: Collection<String>)
    abstract suspend fun add(mediaSourceSave: MediaSourceSave)

    abstract suspend fun updateSave(instanceId: String, config: MediaSourceSave.() -> MediaSourceSave): Boolean

    abstract suspend fun updateSaves(instanceIds: Collection<String>, update: MediaSourceSave.() -> MediaSourceSave)

    abstract suspend fun partiallyReorder(newOrderInstanceIds: List<String>)
}

suspend inline fun MediaSourceInstanceRepository.updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
    return updateSave(instanceId) {
        copy(config = config)
    }
}

@Serializable
data class MediaSourceSaves(
    val instances: List<MediaSourceSave> = emptyList(),
) {
    companion object {
        val Empty = MediaSourceSaves(emptyList())
        val Default: MediaSourceSaves = Empty
    }
}

class MediaSourceInstanceRepositoryImpl(
    private val dataStore: DataStore<MediaSourceSaves>
) : MediaSourceInstanceRepository() {
    override val flow: Flow<List<MediaSourceSave>> = dataStore.data.map { it.instances }
    override suspend fun clear() {
        dataStore.updateData { MediaSourceSaves.Empty }
    }

    override suspend fun remove(instanceId: String) {
        dataStore.updateData { current ->
            current.copy(instances = current.instances.filter { it.instanceId != instanceId })
        }
    }

    override suspend fun removeAll(instanceIds: Collection<String>) {
        val ids = instanceIds.toSet()
        dataStore.updateData { current ->
            current.copy(instances = current.instances.filterNot { it.instanceId in ids })
        }
    }

    override suspend fun add(mediaSourceSave: MediaSourceSave) {
        dataStore.updateData { current ->
            if (current.instances.any { it.instanceId == mediaSourceSave.instanceId }) {
                error("Attempting to add a duplicated MediaSourceSave: $mediaSourceSave")
            }
            current.copy(instances = current.instances + mediaSourceSave)
        }
    }

    override suspend fun updateSave(instanceId: String, config: MediaSourceSave.() -> MediaSourceSave): Boolean {
        var found = false
        dataStore.updateData { current ->
            found = current.instances.any { it.instanceId == instanceId }
            if (found) {
                current.copy(
                    instances = current.instances.map { save ->
                        if (save.instanceId == instanceId) {
                            save.run(config)
                        } else {
                            save
                        }
                    },
                )
            } else {
                current
            }
        }
        return found
    }

    override suspend fun updateSaves(instanceIds: Collection<String>, update: MediaSourceSave.() -> MediaSourceSave) {
        val ids = instanceIds.toSet()
        dataStore.updateData { current ->
            current.copy(
                instances = current.instances.map { save ->
                    if (save.instanceId in ids) {
                        save.update()
                    } else {
                        save
                    }
                },
            )
        }
    }

    override suspend fun partiallyReorder(newOrderInstanceIds: List<String>) {
        dataStore.updateData { current ->
            current.copy(instances = current.instances.partiallyReorderBy({ it.instanceId }, newOrderInstanceIds))
        }
    }
}
