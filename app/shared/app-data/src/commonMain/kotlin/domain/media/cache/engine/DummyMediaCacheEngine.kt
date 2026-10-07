package com.wynime.app.domain.media.cache.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.tools.toProgress
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import kotlin.coroutines.CoroutineContext

class DummyMediaCacheEngine(
    private val mediaSourceId: String,
    private val location: MediaSourceLocation = MediaSourceLocation.Local,
    override val engineKey: MediaCacheEngineKey = Companion.engineKey,
) : MediaCacheEngine {

    override val stats: Flow<MediaStats> = flowOf(MediaStats.Unspecified)

    override fun supports(media: Media): Boolean = true

    override suspend fun restore(
        origin: Media,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext
    ): MediaCache = DummyMediaCache(origin, metadata, mediaSourceId, location)

    override suspend fun createCache(
        origin: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        parentContext: CoroutineContext
    ): MediaCache = DummyMediaCache(origin, metadata, mediaSourceId, location)

    override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
    }

    companion object {
        val engineKey = MediaCacheEngineKey("test-in-memory")
    }
}

class DummyMediaCache(
    override val origin: Media,
    override val metadata: MediaCacheMetadata,
    val mediaSourceId: String,
    val location: MediaSourceLocation = MediaSourceLocation.Local,
) : MediaCache {
    private val cachedMedia by lazy {
        CachedMedia(origin, mediaSourceId, origin.download, location)
    }
    override val state: MutableStateFlow<MediaCacheState> = MutableStateFlow(
        MediaCacheState.IN_PROGRESS,
    )

    override suspend fun getCachedMedia(): CachedMedia = cachedMedia

    override val fileStats: Flow<MediaCache.FileStats> =
        flowOf(MediaCache.FileStats(300.megaBytes, 100.megaBytes))
    override val sessionStats: Flow<MediaCache.SessionStats> =
        flowOf(
            MediaCache.SessionStats(
                0.megaBytes,
                0.megaBytes,
                0.megaBytes,
                0.megaBytes,
                0.megaBytes,
                0f.toProgress(),
            ),
        )

    override suspend fun pause() {
    }

    override suspend fun close() {
    }

    override suspend fun resume() {
    }

    override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun closeAndDeleteFiles() {
        isDeleted.value = true
    }
}
