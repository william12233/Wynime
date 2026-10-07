package com.wynime.app.domain.media.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.cache.engine.MediaCacheEngineKey
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.cache.engine.sum
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.ui.foundation.HasBackgroundScope
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.utils.coroutines.flows.flowOfEmptyList

class MediaDownloadManager(
    val storages: List<MediaCacheStorage>,
    override val backgroundScope: CoroutineScope,
) : HasBackgroundScope {

    private val loadedDownloads: SharedFlow<List<MediaDownload>> = storages
        .map { storage -> storage.listFlow.map { caches -> storage to caches } }
        .let { flows -> if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { it.toList() } }
        .scan(emptyList<MediaDownload>()) { previous, entries ->
            val existing = previous.associateBy { it.cache }

            val next = entries
                .flatMap { (storage, caches) -> caches.map { cache -> storage to cache } }
                .distinctBy { (_, cache) -> cache.cacheId }
                .map { (storage, cache) -> existing[cache] ?: MediaDownload(cache, storage, backgroundScope) }
            val retained = next.toHashSet()
            previous.forEach { download -> if (download !in retained) download.close() }
            next
        }
        .drop(1)
        .shareIn(backgroundScope, SharingStarted.Eagerly, replay = 1)

    val downloads: StateFlow<List<MediaDownload>> =
        loadedDownloads.stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())

    fun downloadsForSubject(subjectId: Int): Flow<List<MediaDownload>> {
        val subjectKey = subjectId.toString()
        return loadedDownloads.map { list -> list.filter { it.metadata.subjectId == subjectKey } }.distinctUntilChanged()
    }

    fun snapshots(subjectId: Int? = null): Flow<List<DownloadSnapshot>> {
        val source = if (subjectId == null) loadedDownloads else downloadsForSubject(subjectId)
        return source.flatMapLatest { it.combineSnapshots() }
    }

    val overallStats: Flow<MediaStats> =
        if (storages.isEmpty()) flowOf(MediaStats.Zero) else storages.map { it.stats }.sum()

    fun downloadStatusForEpisode(subjectId: Int, episodeId: Int): Flow<EpisodeCacheStatus> {
        val subjectKey = subjectId.toString()
        val episodeKey = episodeId.toString()
        return loadedDownloads
            .map { list -> list.filter { it.metadata.subjectId == subjectKey && it.metadata.episodeId == episodeKey } }
            .distinctUntilChanged()
            .flatMapLatest { matching ->
                if (matching.isEmpty()) {
                    flowOf(EpisodeCacheStatus.NotCached)
                } else {
                    combine(matching.map { it.cache.episodeProgress() }) { it.toEpisodeCacheStatus() }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    fun defaultStorageFor(media: Media): MediaCacheStorage {
        val supported = storages.filter { it.engine.supports(media) }
        return supported.firstOrNull()
            ?: throw UnsupportedOperationException("No download storage supports media ${media.mediaId}")
    }

    suspend fun createDownload(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        storage: MediaCacheStorage = defaultStorageFor(media),
    ): MediaCache {
        for (other in storages) {
            if (other === storage) continue
            other.listFlow.first().firstOrNull { it.isSameMediaAndEpisode(media, metadata) }?.let { return it }
        }
        return storage.cache(media, metadata, episodeMetadata)
    }

    fun findDownload(id: String): MediaDownload? = downloads.value.firstOrNull { it.id == id }

    fun downloadOf(cache: MediaCache): MediaDownload? = downloads.value.firstOrNull { it.cache === cache }

    suspend fun findCaches(filter: (MediaCache) -> Boolean): List<MediaCache> =
        storages.flatMap { it.listFlow.first() }.filter(filter)

    suspend fun delete(download: MediaDownload): Boolean = download.storage.delete(download.cache)

    suspend fun deleteDownload(cache: MediaCache): Boolean {
        downloadOf(cache)?.let { return delete(it) }
        return storages.any { it.delete(cache) }
    }

    private fun List<MediaDownload>.combineSnapshots(): Flow<List<DownloadSnapshot>> =
        if (isEmpty()) flowOfEmptyList() else combine(map { it.snapshot }) { it.toList() }

    companion object {

        const val LOCAL_FS_MEDIA_SOURCE_ID = "local-file-system"
    }
}

private fun MediaCache.isSameMediaAndEpisode(media: Media, metadata: MediaCacheMetadata): Boolean =
    origin.mediaId == media.mediaId &&
            this.metadata.subjectId == metadata.subjectId &&
            this.metadata.episodeId == metadata.episodeId

private data class EpisodeProgress(
    val state: MediaCacheState,
    val fileStats: MediaCache.FileStats,
)

private fun MediaCache.episodeProgress(): Flow<EpisodeProgress> =
    combine(state, fileStats) { state, fileStats -> EpisodeProgress(state, fileStats) }

private fun Array<EpisodeProgress>.toEpisodeCacheStatus(): EpisodeCacheStatus {
    firstOrNull { it.state == MediaCacheState.COMPLETED }?.let {
        return EpisodeCacheStatus.Cached(totalSize = it.fileStats.totalSize)
    }
    firstOrNull { it.state == MediaCacheState.IN_PROGRESS || it.state == MediaCacheState.PAUSED }?.let {
        return EpisodeCacheStatus.Caching(progress = it.fileStats.downloadProgress, totalSize = it.fileStats.totalSize)
    }
    return EpisodeCacheStatus.NotCached
}
