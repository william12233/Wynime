package com.wynime.app.domain.media.cache.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import com.wynime.app.domain.media.cache.DownloaderStatus
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.cache.TestMediaCache
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngineKey
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.httpdownloader.DownloadId
import com.wynime.utils.httpdownloader.DownloadState
import com.wynime.utils.httpdownloader.DownloadStatus
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpMediaCacheStorageTest {
    @Test
    fun `overlapping creation calls wait for persistence and reuse the same download`() = runTest {
        verifyOverlappingSubmissions(failCreation = false)
    }

    @Test
    fun `overlapping creation calls report creation failure instead of accepting a placeholder`() = runTest {
        verifyOverlappingSubmissions(failCreation = true)
    }

    private suspend fun TestScope.verifyOverlappingSubmissions(failCreation: Boolean) {
        val gate = CompletableDeferred<Unit>()
        val store = MemoryDataStore<List<MediaCacheSave>>(emptyList())
        val engine = DelayedHttpCacheEngine(gate, failCreation)
        val storage = HttpMediaCacheStorage(
            mediaSourceId = "local-file-system",
            store = store,
            dao = FakeHttpCacheDownloadStateDao,
            httpEngine = engine,
            displayName = "Test HTTP Storage",
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        try {
            val manager = MediaDownloadManager(listOf(storage), backgroundScope)
            val first = async {
                runCatching { manager.createDownload(testMedia(), testMetadata(), testEpisodeMetadata()) }
            }
            runCurrent()
            assertEquals(1, storage.listFlow.value.size)
            val second = async {
                runCatching { manager.createDownload(testMedia(), testMetadata(), testEpisodeMetadata()) }
            }
            runCurrent()
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            assertTrue(store.data.first().isEmpty())

            gate.complete(Unit)
            val outcomes = awaitAll(first, second)
            if (failCreation) {
                outcomes.forEach { assertTrue(it.isFailure) }
                assertTrue(store.data.first().isEmpty())
                assertTrue(storage.listFlow.value.isEmpty())
            } else {
                val ids = outcomes.map { it.getOrThrow().cacheId }
                assertEquals(ids[0], ids[1])
                assertEquals(1, engine.createCalls)
                assertEquals(1, store.data.first().size)
                assertEquals(1, storage.listFlow.value.size)
            }
        } finally {
            storage.close()
        }
    }

    @Test
    fun `deleting a record keeps records of other engines in the shared datastore`() = runTest {
        val foreign = MediaCacheSave(testMedia(), testMetadata(), MediaCacheEngineKey("other"))
        val store = MemoryDataStore<List<MediaCacheSave>>(listOf(foreign))
        val storage = HttpMediaCacheStorage(
            mediaSourceId = "local-file-system",
            store = store,
            dao = FakeHttpCacheDownloadStateDao,
            httpEngine = DelayedHttpCacheEngine(CompletableDeferred(Unit)),
            displayName = "Test HTTP Storage",
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        try {
            val cache = storage.cache(testMedia(), testMetadata(), testEpisodeMetadata())
            runCurrent()
            assertEquals(2, store.data.first().size)

            assertTrue(storage.delete(cache))
            assertEquals(listOf(foreign), store.data.first())
            assertTrue(storage.listFlow.value.isEmpty())
        } finally {
            storage.close()
        }
    }

    @Test
    fun `cache shows placeholder before slow engine creation completes`() = runTest {
        val createGate = CompletableDeferred<Unit>()
        val metadataStore = MemoryDataStore<List<MediaCacheSave>>(emptyList())
        val engine = DelayedHttpCacheEngine(createGate)
        val storage = HttpMediaCacheStorage(
            mediaSourceId = "local-file-system",
            store = metadataStore,
            dao = FakeHttpCacheDownloadStateDao,
            httpEngine = engine,
            displayName = "Test HTTP Storage",
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val media = testMedia()
        val metadata = testMetadata()

        val job = launch {
            storage.cache(
                media = media,
                metadata = metadata,
                episodeMetadata = testEpisodeMetadata(),
                resume = false,
            )
        }

        runCurrent()

        val pending = storage.listFlow.first().single()
        assertSame(media, pending.origin)
        assertEquals(metadata, pending.metadata)
        assertEquals(MediaCacheState.IN_PROGRESS, pending.state.first())
        assertEquals(MediaCache.FileStats.Unspecified, pending.fileStats.first())
        assertEquals(MediaCache.SessionStats.Unspecified, pending.sessionStats.first())
        assertFalse(pending.canPlay.first())
        assertEquals(DownloaderStatus.Resolving, pending.downloaderStatus.first())
        assertEquals(emptyList(), metadataStore.data.first())

        createGate.complete(Unit)
        runCurrent()
        job.join()

        val saved = metadataStore.data.first().single()
        assertEquals(media.mediaId, saved.origin.mediaId)
        assertEquals(metadata, saved.metadata)

        assertEquals(DelayedHttpCacheEngine.DOWNLOADER_STATUS, pending.downloaderStatus.first())
    }
}

private class DelayedHttpCacheEngine(
    private val createGate: CompletableDeferred<Unit>,
    private val failCreation: Boolean = false,
) : MediaCacheEngine {
    var createCalls = 0
        private set

    override val engineKey: MediaCacheEngineKey = MediaCacheEngineKey.WebM3u
    override val stats: Flow<MediaStats> = flowOf(MediaStats.Zero)

    override fun supports(media: Media): Boolean = true

    override suspend fun restore(
        origin: Media,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext,
    ): MediaCache? = null

    override suspend fun createCache(
        origin: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        parentContext: CoroutineContext,
    ): MediaCache {
        createCalls++
        createGate.await()
        check(!failCreation) { "creation failed" }
        return object : TestMediaCache(
            media = CachedMedia(
                origin = origin,
                cacheMediaSourceId = "local-file-system",
                download = ResourceLocation.LocalFile("/tmp/${origin.mediaId}.mp4"),
            ),
            metadata = metadata,
        ) {
            override val downloaderStatus: Flow<DownloaderStatus?> = flowOf(DOWNLOADER_STATUS)
        }
    }

    companion object {
        val DOWNLOADER_STATUS = DownloaderStatus.Http(
            status = DownloadStatus.DOWNLOADING,
            error = null,
            downloadedSegments = 1,
            totalSegments = 4,
        )
    }

    override suspend fun deleteUnusedCaches(all: List<MediaCache>) = Unit
}

private object FakeHttpCacheDownloadStateDao : HttpCacheDownloadStateDao {
    override fun getAll(): Flow<List<DownloadState>> = flowOf(emptyList())

    override suspend fun upsert(state: DownloadState) = Unit

    override suspend fun updateStatus(id: DownloadId, status: DownloadStatus) = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun deleteById(id: DownloadId) = Unit

    override suspend fun getById(id: DownloadId): DownloadState? = null
}

private fun testMedia(): Media {
    return createTestDefaultMedia(
        mediaId = "media-1",
        mediaSourceId = "source-1",
        originalUrl = "https://example.com/media-1",
        download = ResourceLocation.HttpStreamingFile("https://example.com/media-1.m3u8"),
        originalTitle = "Episode 1",
        publishedTime = 1L,
        properties = createTestMediaProperties(
            subjectName = "Test Subject",
            episodeName = "Episode 1",
        ),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )
}

private fun testEpisodeMetadata(): EpisodeMetadata {
    return EpisodeMetadata("Episode 1", EpisodeSort(1), EpisodeSort(1))
}

private fun testMetadata(): MediaCacheMetadata {
    return MediaCacheMetadata(
        subjectId = "1",
        episodeId = "1",
        subjectNameCN = "Test Subject",
        subjectNames = listOf("Test Subject"),
        episodeSort = EpisodeSort(1),
        episodeEp = EpisodeSort(1),
        episodeName = "Episode 1",
    )
}
