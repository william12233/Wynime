package com.wynime.app.domain.media.cache.storage

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.domain.media.cache.LocalFileMediaCache
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.engine.DummyMediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.io.inSystem

class AbstractDataStoreMediaCacheStorageRestoreTest {
    @Test
    fun `completed records of one pack are restored separately and only once`() = runTest {
        val storage = TestStorage(backgroundScope.coroutineContext, listOf("1", "2").map { save(it) })
        assertEquals(listOf("1", "2"), storage.refreshCache().map { it.metadata.episodeId }.sorted())
        assertEquals(listOf("1", "2"), storage.listFlow.first().map { it.metadata.episodeId }.sorted())

        assertEquals(emptyList(), storage.refreshCache())
        assertEquals(listOf("1", "2"), storage.listFlow.first().map { it.metadata.episodeId }.sorted())
    }

    private class TestStorage(
        parentCoroutineContext: CoroutineContext,
        saves: List<MediaCacheSave>,
    ) : AbstractDataStoreMediaCacheStorage(
        mediaSourceId = "test-storage",
        datastore = MemoryDataStore(saves),
        engine = LocalFileEngine,
        displayName = "Test Storage",
        parentCoroutineContext = parentCoroutineContext,
    ) {
        override suspend fun restorePersistedCaches() = Unit
    }

    private object LocalFileEngine : MediaCacheEngine by DummyMediaCacheEngine("test-storage") {
        override suspend fun restore(origin: Media, metadata: MediaCacheMetadata, parentContext: CoroutineContext): MediaCache =
            LocalFileMediaCache(origin, metadata, Path("/cache/${metadata.episodeId}.mkv").inSystem) {}
    }

    private val pack = createTestDefaultMedia(
        mediaId = "pack",
        mediaSourceId = "test-source",
        originalUrl = "https://example.com/pack",
        download = ResourceLocation.HttpStreamingFile("https://example.com/pack.m3u8"),
        originalTitle = "Pack 01-02",
        publishedTime = 1L,
        properties = createTestMediaProperties(subjectName = "Test Subject"),
        episodeRange = EpisodeRange.range(EpisodeSort(1), EpisodeSort(2)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )

    private fun save(episodeId: String) = MediaCacheSave(
        origin = pack,
        metadata = MediaCacheMetadata(
            subjectId = "1",
            episodeId = episodeId,
            subjectNameCN = "Test Subject",
            subjectNames = listOf("Test Subject"),
            episodeSort = EpisodeSort(episodeId.toInt()),
            episodeEp = EpisodeSort(episodeId.toInt()),
            episodeName = "Episode $episodeId",
        ),
        engine = LocalFileEngine.engineKey,
    )
}
