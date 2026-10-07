package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.app.domain.episode.GetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.coroutines.childScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MarkAsWatchedExtensionTest : AbstractPlayerExtensionTest() {
    private val extensionFactory = EpisodePlayerExtensionFactory { context, koin ->
        MarkAsWatchedExtension(context, koin, enableSamplingAndDebounce = false)
    }

    private fun TestScope.createCase(
        videoScaffoldConfigFlow: Flow<VideoScaffoldConfig> = flowOf(
            VideoScaffoldConfig.AllDisabled.copy(autoMarkDone = true),
        ),
        getEpisodeCollectionType: GetEpisodeCollectionTypeUseCase = GetEpisodeCollectionTypeUseCase { _, _, _ -> null },
        setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase = SetEpisodeCollectionTypeUseCase { _, _, _ -> },
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = createSuite(testScope)

        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                videoScaffoldConfigFlow
            }
        }
        suite.registerComponent<GetEpisodeCollectionTypeUseCase> {
            getEpisodeCollectionType
        }
        suite.registerComponent<SetEpisodeCollectionTypeUseCase> {
            setEpisodeCollectionType
        }

        val state = suite.createState(
            extensions = listOf(extensionFactory),
        )
        state.onUIReady()
        return Triple(testScope, suite, state)
    }

    @Test
    fun `does not mark if autoMarkDone is false`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            videoScaffoldConfigFlow = flowOf(
                VideoScaffoldConfig.AllDisabled.copy(autoMarkDone = false),
            ),
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()

        assertFalse(setCalled)

        testScope.cancel()
    }

    @Test
    fun `does mark if not playing`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        suite.player.loadMedia(durationMs = 10000L, playWhenReady = false)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)
        advanceUntilIdle()
        assertFalse(setCalled)

        suite.player.play()

        advanceUntilIdle()

        assertTrue(setCalled)

        testScope.cancel()
    }

    @Test
    fun `does not mark if already DONE or DROPPED`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> UnifiedCollectionType.DONE },
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()

        assertFalse(setCalled)

        testScope.cancel()
    }

    @Test
    fun `marks as watched when playing and above 90 percent`() = runTest {
        var requestedSubjectId: Int? = null
        var requestedEpisodeId: Int? = null
        var requestedType: UnifiedCollectionType? = null

        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { subjectId, episodeId, type ->
                requestedSubjectId = subjectId
                requestedEpisodeId = episodeId
                requestedType = type
            },
        )

        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()

        assertEquals(subjectId, requestedSubjectId)
        assertEquals(initialEpisodeId, requestedEpisodeId)
        assertEquals(UnifiedCollectionType.DONE, requestedType)

        testScope.cancel()
    }

    @Test
    fun `marks only once for the same episode`() = runTest {
        var callCount = 0
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { _, _, _ ->
                callCount++
            },
        )

        suite.player.loadMedia(durationMs = 10000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(9500L)

        advanceUntilIdle()
        assertEquals(1, callCount)

        suite.player.injectPosition(9999L)
        advanceUntilIdle()

        assertEquals(1, callCount)

        testScope.cancel()
    }

    @Test
    fun `marks as watched for 3min`() = runTest {
        var requestedSubjectId: Int? = null
        var requestedEpisodeId: Int? = null
        var requestedType: UnifiedCollectionType? = null

        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { subjectId, episodeId, type ->
                requestedSubjectId = subjectId
                requestedEpisodeId = episodeId
                requestedType = type
            },
        )

        suite.player.loadMedia(durationMs = 3.minutes.inWholeMilliseconds, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition((3.minutes - 100.seconds).inWholeMilliseconds)

        advanceUntilIdle()

        assertEquals(subjectId, requestedSubjectId)
        assertEquals(initialEpisodeId, requestedEpisodeId)
        assertEquals(UnifiedCollectionType.DONE, requestedType)

        testScope.cancel()
    }

    @Test
    fun `marks as watched when within last 100 seconds`() = runTest {
        var requestedSubjectId: Int? = null
        var requestedEpisodeId: Int? = null
        var requestedType: UnifiedCollectionType? = null

        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { subjectId, episodeId, type ->
                requestedSubjectId = subjectId
                requestedEpisodeId = episodeId
                requestedType = type
            },
        )

        suite.player.loadMedia(durationMs = 1_200_000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition((18.minutes + 21.seconds).inWholeMilliseconds)

        advanceUntilIdle()

        assertEquals(subjectId, requestedSubjectId)
        assertEquals(initialEpisodeId, requestedEpisodeId)
        assertEquals(UnifiedCollectionType.DONE, requestedType)

        testScope.cancel()
    }

    @Test
    fun `does not mark when neither at 90 percent nor within last 100 seconds`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        suite.player.loadMedia(durationMs = 1_200_000L, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition(960_000L)

        advanceUntilIdle()

        assertFalse(setCalled)

        testScope.cancel()
    }

    @Test
    fun `does not mark when video is shorter than 10 seconds`() = runTest {
        var setCalled = false
        val (testScope, suite, _) = createCase(
            getEpisodeCollectionType = { _, _, _ -> null },
            setEpisodeCollectionType = { _, _, _ ->
                setCalled = true
            },
        )

        suite.player.loadMedia(durationMs = 9.seconds.inWholeMilliseconds, playWhenReady = true)
        advanceUntilIdle()

        suite.player.injectPosition((9.seconds.inWholeMilliseconds * 0.95).toLong())

        advanceUntilIdle()

        assertFalse(setCalled)

        testScope.cancel()
    }

    private fun TestScope.createSuite(scope: CoroutineScope): EpisodePlayerTestSuite {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return EpisodePlayerTestSuite(this, scope)
    }
}
