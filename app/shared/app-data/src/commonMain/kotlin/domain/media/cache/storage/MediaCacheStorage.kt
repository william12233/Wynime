package com.wynime.app.domain.media.cache.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.engine.DummyMediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngineKey
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.flowOfFileSizeZero

interface MediaCacheStorage : AutoCloseable {

    val mediaSourceId: String

    val engine: MediaCacheEngine

    val stats: Flow<MediaStats>

    val listFlow: Flow<List<MediaCache>>

    suspend fun restorePersistedCaches()

    suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean = true,
    ): MediaCache

    suspend fun delete(cache: MediaCache): Boolean =
        deleteFirst { it == cache }

    suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean
}

@Serializable
data class MediaCacheSave(
    val origin: Media,
    val metadata: MediaCacheMetadata,

    val engine: MediaCacheEngineKey,
) {
}

val MediaCacheStorage.totalSize: Flow<FileSize>
    get() = listFlow.flatMapLatest { caches ->
        if (caches.isEmpty()) {
            return@flatMapLatest flowOfFileSizeZero
        }
        combine(caches.map { cache -> cache.fileStats.map { it.totalSize } }) { sizes ->
            sizes.sumOf { it.inBytes }.bytes
        }
    }

val MediaCacheStorage.count: Flow<Int>
    get() = listFlow.map { it.size }

suspend inline fun MediaCacheStorage.contains(cache: MediaCache): Boolean =
    listFlow.first().any { it === cache }

interface MediaSaveDirProvider {
    val saveDir: String
}

class TestMediaCacheStorage : MediaCacheStorage {
    override val mediaSourceId: String
        get() = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID
    override val engine: MediaCacheEngine = DummyMediaCacheEngine(mediaSourceId)
    override val listFlow: MutableStateFlow<List<MediaCache>> =
        MutableStateFlow(listOf())

    override suspend fun restorePersistedCaches() {
    }

    override val stats: Flow<MediaStats> = flowOf(MediaStats.Unspecified)

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean
    ): MediaCache {
        throw UnsupportedOperationException()
    }

    override suspend fun delete(cache: MediaCache): Boolean {
        if (listFlow.first().any { it == cache }) {
            listFlow.value = listFlow.first().filter { it != cache }
            return true
        }
        return false
    }

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val list = listFlow.first()
        val cache = list.firstOrNull(predicate) ?: return false
        listFlow.value = list.filter { it != cache }
        return true
    }

    override fun close() {
    }
}

