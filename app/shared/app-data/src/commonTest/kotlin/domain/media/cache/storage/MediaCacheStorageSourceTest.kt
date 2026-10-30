package com.wynime.app.domain.media.cache.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.TestMediaCache
import com.wynime.app.domain.media.cache.engine.DummyMediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.app.domain.media.fetch.MediaFetcherConfig
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.fetch.MediaSourceMediaFetcher
import com.wynime.app.domain.mediasource.instance.createTestMediaSourceInstance
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.paging.SinglePagePagedSource
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.TestHttpMediaSource
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaCacheStorageSourceTest {
    @Test
    fun `in progress cache is not exposed until it becomes playable`() = runTest {
        val storage = TestMediaCacheStorage()
        val playable = MutableStateFlow(false)
        val cache = testCache(playable)
        storage.listFlow.value = listOf(cache)
        val source = MediaCacheStorageSource(storage, "Test cache")

        assertEquals(emptyList(), source.fetch(testRequest()).results.toList())

        playable.value = true

        assertEquals(1, source.fetch(testRequest()).results.toList().size)
    }

    @Test
    fun `first source query waits for cache restoration`() = runTest {
        val storage = GatedStorage()
        val source = MediaCacheStorageSource(storage, "Test cache")
        val cache = testCache(MutableStateFlow(true))
        val result = async {
            source.fetch(testRequest()).results.toList()
        }

        runCurrent()
        assertFalse(result.isCompleted)

        storage.listFlow.value = listOf(cache)
        storage.restored.complete(Unit)

        assertEquals(1, result.await().size)
    }

    @Test
    fun `cache list and playable state changes emit source invalidations`() = runTest {
        val storage = TestMediaCacheStorage()
        val playable = MutableStateFlow(false)
        val cache = testCache(playable)
        val source = MediaCacheStorageSource(storage, "Test cache")
        val invalidations = async {
            source.invalidations.take(2).toList()
        }

        runCurrent()
        storage.listFlow.value = listOf(cache)
        runCurrent()
        playable.value = true

        assertEquals(2, invalidations.await().size)
    }

    @Test
    fun `playable cache is published while web source is still working`() = runTest {
        val storage = TestMediaCacheStorage()
        val localSource = MediaCacheStorageSource(storage, "Test cache")
        val webGate = CompletableDeferred<Unit>()
        val webSource = TestHttpMediaSource(
            mediaSourceId = "web-source",
            fetch = {
                SinglePagePagedSource {
                    flow<MediaMatch> {
                        webGate.await()
                    }
                }
            },
        )
        val fetcher = MediaSourceMediaFetcher(
            configProvider = { MediaFetcherConfig.Default },
            mediaSources = listOf(
                createTestMediaSourceInstance(localSource),
                createTestMediaSourceInstance(webSource),
            ),
            flowContext = currentCoroutineContext(),
        )
        val session = fetcher.newSession(testRequest())
        val cache = testCache(MutableStateFlow(true))
        val cachedResult = async {
            session.cumulativeResults.first { medias ->
                medias.any { it.kind == MediaSourceKind.LocalCache }
            }
        }

        runCurrent()
        assertEquals(MediaSourceFetchState.Working, session.mediaSourceResults[1].state.value)

        storage.listFlow.value = listOf(cache)

        assertEquals(1, cachedResult.await().count { it.kind == MediaSourceKind.LocalCache })
        webGate.complete(Unit)
    }

    @Test
    fun `deleting a cache removes it from an already running source result`() = runTest {
        val storage = TestMediaCacheStorage()
        val localSource = MediaCacheStorageSource(storage, "Test cache")
        val webGate = CompletableDeferred<Unit>()
        val webSource = TestHttpMediaSource(
            mediaSourceId = "web-source",
            fetch = {
                SinglePagePagedSource {
                    flow<MediaMatch> {
                        webGate.await()
                    }
                }
            },
        )
        val cache = testCache(MutableStateFlow(true))
        storage.listFlow.value = listOf(cache)
        val fetcher = MediaSourceMediaFetcher(
            configProvider = { MediaFetcherConfig.Default },
            mediaSources = listOf(
                createTestMediaSourceInstance(localSource),
                createTestMediaSourceInstance(webSource),
            ),
            flowContext = currentCoroutineContext(),
        )
        val session = fetcher.newSession(testRequest())
        val initial = CompletableDeferred<Unit>()
        val removed = CompletableDeferred<Unit>()
        val observer = launch {
            session.cumulativeResults.collect { medias ->
                val hasCache = medias.any { it.kind == MediaSourceKind.LocalCache }
                if (hasCache) {
                    initial.complete(Unit)
                } else if (initial.isCompleted) {
                    removed.complete(Unit)
                }
            }
        }
        runCurrent()
        initial.await()

        storage.listFlow.value = emptyList()

        removed.await()
        observer.cancel()
        webGate.complete(Unit)
    }

    private fun testCache(playable: MutableStateFlow<Boolean>): MediaCache {
        val origin = createTestDefaultMedia(
            mediaId = "cached-media",
            mediaSourceId = "web-source",
            originalUrl = "https://example.com/cached-media",
            download = ResourceLocation.HttpStreamingFile("https://example.com/cached-media.m3u8"),
            originalTitle = "Episode 1",
            publishedTime = 1L,
            properties = createTestMediaProperties(subjectName = "Test Subject"),
            episodeRange = EpisodeRange.single(EpisodeSort(1)),
            location = MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
        )
        return object : TestMediaCache(
            media = CachedMedia(
                origin = origin,
                cacheMediaSourceId = "local-file-system",
                download = ResourceLocation.LocalFile("/tmp/cached-media.mp4"),
            ),
            metadata = MediaCacheMetadata(
                subjectId = "1",
                episodeId = "1",
                subjectNameCN = "Test Subject",
                subjectNames = listOf("Test Subject"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "Episode 1",
            ),
        ) {
            override val canPlay: Flow<Boolean> = playable
        }
    }

    private fun testRequest() = MediaFetchRequest(
        subjectId = "1",
        episodeId = "1",
        subjectNames = listOf("Test Subject"),
        episodeSort = EpisodeSort(1),
        episodeName = "Episode 1",
        episodeEp = EpisodeSort(1),
    )
}

private class GatedStorage : MediaCacheStorage {
    val restored = CompletableDeferred<Unit>()
    override val mediaSourceId: String = "test-storage"
    override val cacheMediaSource: MediaSource get() = error("Not used")
    override val engine: MediaCacheEngine = DummyMediaCacheEngine(mediaSourceId)
    override val stats: Flow<MediaStats> = flowOf(MediaStats.Zero)
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())

    override suspend fun awaitRestoration() {
        restored.await()
    }

    override suspend fun restorePersistedCaches() = Unit

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean,
    ): MediaCache = error("Not used")

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean = false
    override fun close() = Unit
}
