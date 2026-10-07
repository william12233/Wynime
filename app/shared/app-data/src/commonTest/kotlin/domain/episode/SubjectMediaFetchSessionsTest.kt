package com.wynime.app.domain.episode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.fetch.CompletedConditions
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind

class SubjectMediaFetchSessionsTest {
    private fun request(episodeId: Int, episodeIds: List<Int> = listOf(1, 2), subjectId: String = "1") = MediaFetchRequest(
        subjectId = subjectId,
        episodeId = episodeId.toString(),
        subjectNames = listOf("Subject"),
        episodeSort = EpisodeSort(episodeId),
        episodeName = "Episode $episodeId",
        episodes = episodeIds.map { MediaFetchRequest.Episode(it.toString(), EpisodeSort(it)) },
    )

    @Test
    fun `same subject reuses the session and keeps it subscribed`() = runTest {
        val created = mutableListOf<FakeSession>()
        val sessions = SubjectMediaFetchSessions(backgroundScope) { FakeSession(it).also { s -> created += s } }

        val first = sessions.get(request(1))
        runCurrent()
        assertEquals(1, created.size)
        assertEquals(1, created[0].subscribers)

        val second = sessions.get(request(2))
        runCurrent()
        assertSame(first, second)
        assertEquals(1, created.size)
        assertEquals(1, created[0].subscribers)
        sessions.close()
    }

    @Test
    fun `different subject query creates a new session and unsubscribes the old one`() = runTest {
        val created = mutableListOf<FakeSession>()
        val sessions = SubjectMediaFetchSessions(backgroundScope) { FakeSession(it).also { s -> created += s } }

        val first = sessions.get(request(1))
        runCurrent()
        val second = sessions.get(request(1, episodeIds = listOf(1, 2, 3)))
        runCurrent()
        assertNotSame(first, second)
        assertEquals(0, created[0].subscribers)
        assertEquals(1, created[1].subscribers)

        sessions.close()
        runCurrent()
        assertEquals(0, created[1].subscribers)
    }

    @Test
    fun `reusing restarts failed and abandoned sources only`() = runTest {
        val failed = FakeSource("failed", MediaSourceFetchState.Failed(IllegalStateException(), 0))
        val abandoned = FakeSource("abandoned", MediaSourceFetchState.Abandoned(IllegalStateException(), 0))
        val working = FakeSource("working", MediaSourceFetchState.Working)
        val succeed = FakeSource("succeed", MediaSourceFetchState.Succeed(0))
        val sessions = SubjectMediaFetchSessions(backgroundScope) {
            FakeSession(it, listOf(failed, abandoned, working, succeed))
        }

        sessions.get(request(1))
        assertEquals(listOf(0, 0, 0, 0), listOf(failed, abandoned, working, succeed).map { it.restarts })
        sessions.get(request(2))
        assertEquals(listOf(1, 1, 0, 0), listOf(failed, abandoned, working, succeed).map { it.restarts })
        sessions.close()
    }

    private class FakeSession(
        request: MediaFetchRequest,
        override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList(),
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
        override fun setFetchRequest(request: MediaFetchRequest) = Unit
    }

    private class FakeSource(id: String, initial: MediaSourceFetchState) : MediaSourceFetchResult {
        var restarts = 0
        override val instanceId = id
        override val mediaSourceId = id
        override val sourceInfo = MediaSourceInfo(id)
        override val kind = MediaSourceKind.WEB
        override val state = MutableStateFlow(initial)
        override val results: Flow<List<Media>> = flowOf(emptyList())
        override fun restart() {
            restarts++
        }

        override fun enable() = Unit
    }
}
