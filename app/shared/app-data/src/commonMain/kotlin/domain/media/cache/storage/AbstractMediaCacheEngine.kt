package com.wynime.app.domain.media.cache.storage

import androidx.datastore.core.DataStore
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import com.wynime.app.domain.media.cache.LocalFileMediaCache
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.cache.engine.sum
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.coroutines.update
import com.wynime.utils.logging.info
import com.wynime.utils.logging.thisLogger
import kotlin.coroutines.CoroutineContext

abstract class AbstractDataStoreMediaCacheStorage(
    override val mediaSourceId: String,
    private val datastore: DataStore<List<MediaCacheSave>>,
    override val engine: MediaCacheEngine,
    private val displayName: String,
    parentCoroutineContext: CoroutineContext,
) : MediaCacheStorage {
    protected val logger = thisLogger()
    protected val scope: CoroutineScope = parentCoroutineContext.childScope()

    protected val metadataFlow = datastore.data
        .map { list ->
            list.filter { it.engine == engine.engineKey }
                .sortedBy { it.origin.mediaId }
        }

    protected val restoredLocalFileMediaCacheIds = MutableStateFlow(persistentListOf<String>())

    open suspend fun refreshCache(): List<MediaCache> {
        val allRecovered = MutableStateFlow(persistentListOf<MediaCache>())
        val metadataFlowSnapshot = metadataFlow.first()
        logger.info { "Restoring media cache, cache count in datastore: ${metadataFlowSnapshot.size}" }
        val semaphore = Semaphore(8)

        supervisorScope {
            metadataFlowSnapshot.forEach { (origin, metadata, _) ->
                if (MediaCache.calculateCacheId(origin.mediaId, metadata) in restoredLocalFileMediaCacheIds.value) return@forEach

                semaphore.acquire()
                @OptIn(DelicateCoroutinesApi::class)
                launch(start = CoroutineStart.ATOMIC) {
                    try {
                        restoreFile(origin, metadata) {
                            if (it is LocalFileMediaCache) {
                                restoredLocalFileMediaCacheIds.update { plus(it.cacheId) }
                            }
                            allRecovered.update { plus(it) }
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }

        listFlow.update {
            allRecovered.value +
                    listFlow.value.filter { it.cacheId in restoredLocalFileMediaCacheIds.value }
        }
        return allRecovered.value
    }

    open suspend fun restoreFile(
        origin: Media,
        metadata: MediaCacheMetadata,
        reportRecovered: suspend (MediaCache) -> Unit,
    ): MediaCache? {
        val cache = engine.restore(origin, metadata, scope.coroutineContext) ?: return null
        logger.info { "Cache restored: ${origin.mediaId}, result=${cache}" }

        reportRecovered(cache)
        cache.resume()

        logger.info { "Cache resumed: $cache" }
        return cache
    }

    override val listFlow: MutableStateFlow<List<MediaCache>> = MutableStateFlow(emptyList())

    override val cacheMediaSource: MediaSource by lazy {
        MediaCacheStorageSource(this, displayName, MediaSourceLocation.Local)
    }
    override val stats: Flow<MediaStats> = listFlow.flatMapLatest { caches ->
        if (caches.isEmpty()) {
            return@flatMapLatest flowOf(MediaStats.Zero)
        }

        combine(
            caches.distinctBy { it.origin.download.uri }.map { cache ->
                cache.sessionStats.map { stats ->
                    MediaStats(
                        uploaded = stats.uploadedBytes,
                        downloaded = stats.downloadedBytes,
                        uploadSpeed = stats.uploadSpeed,
                        downloadSpeed = stats.downloadSpeed,
                    )
                }
            },
        ) { cacheStats ->
            cacheStats.sum()
        }
    }

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean
    ): MediaCache {
        logger.info { "$mediaSourceId creating cache, metadata=$metadata" }
        listFlow.value.firstOrNull {
            isSameMediaAndEpisode(it, media, metadata)
        }?.let { return it }

        if (!engine.supports(media)) {
            throw UnsupportedOperationException("Engine does not support media: $media")
        }
        val cache = engine.createCache(
            media, metadata,
            episodeMetadata,
            scope.coroutineContext,
        )

        withContext(Dispatchers.IO_) {
            datastore.updateData { list ->
                list + MediaCacheSave(cache.origin, cache.metadata, engine.engineKey)
            }
        }

        listFlow.update { plus(cache) }

        if (resume) {
            cache.resume()
        }

        return cache
    }

    override suspend fun delete(cache: MediaCache): Boolean {
        return deleteFirst { isSameMediaAndEpisode(it, cache.origin, cache.metadata) }
    }

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val cache = listFlow.value.firstOrNull(predicate) ?: return false
        listFlow.update { minus(cache) }
        restoredLocalFileMediaCacheIds.update { minus(cache.cacheId) }
        withContext(Dispatchers.IO_) {

            datastore.updateData { list ->
                list.filterNot { it.engine == engine.engineKey && isSameMediaAndEpisode(cache, it) }
            }
        }
        cache.closeAndDeleteFiles()
        return true
    }

    override fun close() {
        scope.cancel()
    }

    protected fun isSameMediaAndEpisode(
        cache: MediaCache,
        media: Media,
        metadata: MediaCacheMetadata = cache.metadata
    ) = cache.origin.mediaId == media.mediaId &&
            metadata.subjectId == cache.metadata.subjectId &&
            metadata.episodeId == cache.metadata.episodeId

    protected fun isSameMediaAndEpisode(cache: MediaCache, save: MediaCacheSave): Boolean =
        isSameMediaAndEpisode(cache, save.origin, save.metadata)
}
