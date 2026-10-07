package com.wynime.app.domain.media.cache.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import com.wynime.app.data.persistent.database.dao.HttpCacheDownloadStateDao
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.httpdownloader.DownloadId
import com.wynime.utils.httpdownloader.DownloadOptions
import com.wynime.utils.httpdownloader.DownloadProgress
import com.wynime.utils.httpdownloader.DownloadState
import com.wynime.utils.httpdownloader.DownloadStatus
import com.wynime.utils.httpdownloader.HttpDownloader
import com.wynime.utils.httpdownloader.MediaType
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HttpDownloadIdentityTest {
    @Test
    fun `season episodes create different HTTP files and deleting one keeps the other`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/season.m3u8"),
        )
        val firstCache = engine.createCache(media, testMetadata(1), testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val firstId = downloader.states.keys.single()
        val secondCache = engine.createCache(media, testMetadata(2), testEpisodeMetadata(2), backgroundScope.coroutineContext)
        val secondId = downloader.states.keys.single { it != firstId }
        assertEquals(2, downloader.states.size)
        assertEquals(2, downloader.states.values.map { it.relativeOutputPath }.distinct().size)
        assertEquals(2, downloader.states.values.map { it.url }.distinct().size)
        firstCache.closeAndDeleteFiles()
        assertEquals(setOf(secondId), downloader.states.keys)
        secondCache.resume()
        assertEquals(secondId, downloader.resumed.last())
    }

    @Test
    fun `restoration reuses mediaId-derived files and prefers episode identity when present`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            mediaId = "source/legacy",
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/legacy.mp4"),
        )
        val metadata = testMetadata(1)
        val mediaIdDerived = DownloadId("source-legacy")
        downloader.downloadWithId(mediaIdDerived, "https://example.com/legacy.mp4", DownloadOptions())
        engine.restore(media, metadata, backgroundScope.coroutineContext)
        assertEquals(mediaIdDerived, downloader.resumed.last())
        engine.createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val currentId = downloader.states.keys.single { it != mediaIdDerived }
        engine.restore(media, metadata, backgroundScope.coroutineContext)
        assertEquals(currentId, downloader.resumed.last())
        assertTrue(mediaIdDerived in downloader.states)
    }

    @Test
    fun `restoration recreates task from dao record with episode identity`() = runTest {
        val downloader = FakeDownloader()
        val engine = engine(downloader)
        val media = TestMediaList.first().copy(
            mediaId = "source/legacy",
            kind = MediaSourceKind.WEB,
            download = ResourceLocation.HttpStreamingFile("https://example.com/legacy.mp4"),
        )
        val metadata = testMetadata(1)
        engine.createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
        val currentId = downloader.states.keys.single()

        downloader.persistedOnly += currentId
        val restored = engine.restore(media, metadata, backgroundScope.coroutineContext)
        assertTrue(restored != null)
        assertEquals(listOf(currentId), downloader.recreated)
        assertEquals(setOf(currentId), downloader.states.keys)
    }

    @Test
    fun `identity is stable filesystem safe and distinguishes episodes subjects and media`() = runTest {
        val media = TestMediaList.first()
        suspend fun createId(media: Media, metadata: MediaCacheMetadata): DownloadId {
            val downloader = FakeDownloader()
            engine(downloader).createCache(media, metadata, testEpisodeMetadata(1), backgroundScope.coroutineContext)
            return downloader.states.keys.single()
        }

        val id = createId(media, testMetadata(1))
        assertEquals(id, createId(media, testMetadata(1).copy(creationTime = 0)))
        assertNotEquals(id, createId(media, testMetadata(2)))
        assertNotEquals(id, createId(media, testMetadata(1).copy(subjectId = "2")))
        assertTrue(id.value.matches(Regex("http-v2-[0-9a-f]{64}")))
        val slash = TestMediaList.first().copy(mediaId = "source/path")
        val colon = slash.copy(mediaId = "source:path")
        assertNotEquals(createId(slash, testMetadata(1)), createId(colon, testMetadata(1)))
    }

    private fun testMetadata(episodeId: Int, subjectId: Int = 1) = MediaCacheMetadata(
        subjectId = subjectId.toString(),
        episodeId = episodeId.toString(),
        subjectNames = listOf("Subject"),
        episodeSort = EpisodeSort(episodeId),
        episodeEp = EpisodeSort(episodeId),
        episodeName = "Episode $episodeId",
    )

    private fun testEpisodeMetadata(episodeId: Int) =
        EpisodeMetadata("Episode $episodeId", EpisodeSort(episodeId), EpisodeSort(episodeId))

    private fun engine(downloader: FakeDownloader) = HttpMediaCacheEngine(
        downloader,
        Path("/unused-test-downloads"),
        object : MediaResolver by TestUniversalMediaResolver {
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> =
                object : MediaDataProvider<UriMediaData> {
                    override val extraFiles = MediaExtraFiles.EMPTY
                    override suspend fun open(scopeForCleanup: CoroutineScope) = UriMediaData("https://example.com/${episode.sort}.mp4")
                }
        },
        "test-storage",
        object : HttpCacheDownloadStateDao {
            override fun getAll() = flowOf(downloader.persisted.values.toList())
            override suspend fun upsert(state: DownloadState) {
                downloader.states[state.downloadId] = state
            }

            override suspend fun updateStatus(id: DownloadId, status: DownloadStatus) = Unit
            override suspend fun deleteAll() {
                downloader.states.clear()
            }

            override suspend fun deleteById(id: DownloadId) {
                downloader.states.remove(id)
            }

            override suspend fun getById(id: DownloadId) = downloader.persisted[id]
        },
    )
}

private class FakeDownloader : HttpDownloader {
    val states = mutableMapOf<DownloadId, DownloadState>()
    val resumed = mutableListOf<DownloadId>()
    val recreated = mutableListOf<DownloadId>()
    val persistedOnly = mutableSetOf<DownloadId>()

    val persisted: Map<DownloadId, DownloadState> get() = states

    override val progressFlow: Flow<DownloadProgress> = flowOf()
    override val downloadStatesFlow: Flow<List<DownloadState>> = flowOf(emptyList())
    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> = flowOf()
    override suspend fun init() = Unit
    override suspend fun download(url: String, options: DownloadOptions): DownloadId = error("unused")
    override suspend fun downloadWithId(downloadId: DownloadId, url: String, options: DownloadOptions): DownloadState {
        if (persistedOnly.remove(downloadId)) recreated += downloadId
        return states.getOrPut(downloadId) {
            DownloadState(
                downloadId, url, "${downloadId.value}.mp4", emptyList(), 0, 0, 0,
                DownloadStatus.DOWNLOADING, relativeSegmentCacheDir = "segments_${downloadId.value}",
                requestHeaders = options.headers, mediaType = MediaType.MP4,
            )
        }
    }

    override suspend fun resume(downloadId: DownloadId): Boolean {
        resumed += downloadId
        return true
    }

    override suspend fun getActiveDownloadIds() = states.keys.toList()
    override suspend fun pause(downloadId: DownloadId) = true
    override suspend fun pauseAll() = states.keys.toList()
    override suspend fun cancel(downloadId: DownloadId) = true
    override suspend fun cancelAll() = Unit
    override suspend fun remove(downloadId: DownloadId) = states.remove(downloadId) != null
    override suspend fun getState(downloadId: DownloadId) = states[downloadId]?.takeUnless { downloadId in persistedOnly }
    override suspend fun getAllStates() = states.values.toList()
    override fun close() = Unit
}
