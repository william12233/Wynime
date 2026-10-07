package com.wynime.app.domain.media.fetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaFetchRequest
import kotlin.time.Duration.Companion.seconds

class SubjectMediaFetchSessionRegistryTest {
    @Test
    fun `playback and download requests share the same short lived discovery snapshot`() = runTest {
        val created = mutableListOf<FakeSession>()
        val registry = SubjectMediaFetchSessionRegistry(
            scope = backgroundScope,
            createSession = { request -> FakeSession(request).also(created::add) },
            expiry = 90.seconds,
        )

        val playbackRequest = request(episodeId = 1, traceId = "playback-trace")
        val downloadRequest = request(episodeId = 2, traceId = "download-trace")
        val playback = registry.get(playbackRequest)
        val download = registry.get(downloadRequest)
        runCurrent()

        assertSame(playback, download)
        assertEquals(1, created.size)
        assertEquals(1, created.single().subscribers)

        registry.release(playback)
        registry.release(download)
        registry.close()
        runCurrent()
        assertEquals(0, created.single().subscribers)
    }

    @Test
    fun `explicit invalidation rebuilds the discovery snapshot`() = runTest {
        val created = mutableListOf<FakeSession>()
        val registry = SubjectMediaFetchSessionRegistry(
            scope = backgroundScope,
            createSession = { request -> FakeSession(request).also(created::add) },
        )
        val request = request(episodeId = 1)

        val first = registry.get(request)
        registry.invalidate(request)
        val second = registry.get(request)

        assertNotSame(first, second)
        assertEquals(2, created.size)
        registry.close()
    }

    @Test
    fun `expired session is rebuilt instead of reusing an old snapshot`() = runTest {
        val created = mutableListOf<FakeSession>()
        var now = 0L
        val registry = SubjectMediaFetchSessionRegistry(
            scope = backgroundScope,
            createSession = { request -> FakeSession(request).also(created::add) },
            expiry = 90.seconds,
            nowMillis = { now },
        )

        val first = registry.get(request(episodeId = 1))
        now = 90.seconds.inWholeMilliseconds + 1
        val second = registry.get(request(episodeId = 2))

        assertNotSame(first, second)
        assertEquals(2, created.size)
        registry.close()
    }

    @Test
    fun `different subjects never share a discovery snapshot`() = runTest {
        val created = mutableListOf<FakeSession>()
        val registry = SubjectMediaFetchSessionRegistry(
            scope = backgroundScope,
            createSession = { request -> FakeSession(request).also(created::add) },
        )

        val first = registry.get(request(episodeId = 1, subjectId = "subject-1"))
        val second = registry.get(request(episodeId = 1, subjectId = "subject-2"))

        assertNotSame(first, second)
        assertEquals(2, created.size)
        registry.close()
    }

    private fun request(
        episodeId: Int,
        traceId: String = "trace-$episodeId",
        subjectId: String = "subject-1",
    ) = MediaFetchRequest(
        subjectId = subjectId,
        episodeId = episodeId.toString(),
        subjectNames = listOf("葬送的芙莉蓮", "Sousou no Frieren"),
        episodeSort = EpisodeSort(episodeId),
        episodeName = "Episode $episodeId",
        traceId = traceId,
        episodes = listOf(
            MediaFetchRequest.Episode("1", EpisodeSort(1)),
            MediaFetchRequest.Episode("2", EpisodeSort(2)),
        ),
    )

    private class FakeSession(
        request: MediaFetchRequest,
    ) : MediaFetchSession {
        var subscribers = 0

        override val request: Flow<MediaFetchRequest> = flowOf(request)
        override val cumulativeResults: Flow<List<Media>> = flow {
            subscribers++
            try {
                emit(emptyList())
                awaitCancellation()
            } finally {
                subscribers--
            }
        }
        override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)
        override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()

        override fun setFetchRequest(request: MediaFetchRequest) = Unit
    }
}
