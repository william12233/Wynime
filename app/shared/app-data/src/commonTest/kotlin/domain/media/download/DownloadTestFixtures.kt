package com.wynime.app.domain.media.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.TestMediaCache
import com.wynime.app.domain.media.cache.engine.DummyMediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngineKey
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.ResourceLocation

internal class DownloadTestCache(
    media: CachedMedia,
    metadata: MediaCacheMetadata,
) : TestMediaCache(media, metadata) {
    override val canPlay = MutableStateFlow(true)

    var pauseCalls = 0
        private set
    var resumeCalls = 0
        private set

    var onPause: suspend () -> Unit = {}

    var onResume: suspend () -> Unit = {}

    override suspend fun pause() {
        pauseCalls++
        onPause()
        super.pause()
    }

    override suspend fun resume() {
        resumeCalls++
        onResume()
        super.resume()
    }
}

internal class FailingStatsCache(
    private val delegate: DownloadTestCache,
    private val failures: Int = Int.MAX_VALUE,
) : MediaCache by delegate {

    var attempts = 0
        private set

    override val fileStats: Flow<MediaCache.FileStats> = flow {
        attempts++
        check(attempts > failures) { "file stats unavailable" }
        emitAll(delegate.fileStats)
    }
}

internal fun testMetadata(episodeId: Int, subjectId: Int = 1): MediaCacheMetadata = MediaCacheMetadata(
    subjectId = subjectId.toString(),
    episodeId = episodeId.toString(),
    subjectNames = listOf("Subject"),
    episodeSort = EpisodeSort(episodeId),
    episodeName = "Episode $episodeId",
)

internal fun testDownload(
    id: Int,
    subjectId: Int = 1,
    range: EpisodeRange? = null,
    episodeId: Int = id,
): DownloadTestCache {
    val media = TestMediaList.first().copy(mediaId = "media-$id", episodeRange = range)
    return DownloadTestCache(
        CachedMedia(media, "test-storage", ResourceLocation.LocalFile("/download-$id")),
        testMetadata(episodeId, subjectId),
    )
}

internal class DownloadTestStorage(
    override val engine: MediaCacheEngine = DummyMediaCacheEngine("test-storage"),
    override val mediaSourceId: String = "test-storage",
) : MediaCacheStorage {
    override val cacheMediaSource: MediaSource get() = error("Not used")
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
    override val stats = MutableStateFlow(MediaStats.Zero)

    var create: suspend (Media, MediaCacheMetadata, EpisodeMetadata) -> MediaCache = { _, _, _ -> error("Not used") }
    var onDelete: suspend (MediaCache) -> Unit = {}

    override suspend fun restorePersistedCaches() = Unit

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean,
    ): MediaCache = create(media, metadata, episodeMetadata)

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val selected = listFlow.value.firstOrNull(predicate) ?: return false
        onDelete(selected)
        listFlow.value -= selected
        return true
    }

    override fun close() = Unit
}

internal fun testDownloadEngine(key: MediaCacheEngineKey, supports: Boolean = true): MediaCacheEngine =
    object : MediaCacheEngine by DummyMediaCacheEngine("test-storage") {
        override val engineKey: MediaCacheEngineKey = key
        override fun supports(media: Media): Boolean = supports
    }
