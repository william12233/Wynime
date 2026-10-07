@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.createExceptionCapturingSupervisorScope
import com.wynime.app.domain.episode.getCurrentEpisodeId
import com.wynime.app.domain.episode.mediaSelectorFlow
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.app.domain.media.resolver.UnsupportedMediaException
import com.wynime.app.domain.player.ExtensionException
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.datasources.api.Media
import com.wynime.utils.coroutines.childScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwitchNextEpisodeExtensionTest : AbstractPlayerExtensionTest() {
    private suspend fun TestScope.loadSelectedMedia(
        suite: EpisodePlayerTestSuite,
        state: EpisodeFetchSelectPlayState,
        durationMillis: Long? = 100_000L,
        mediaIndex: Int = 0,
    ) {
        val media = TestMediaList[mediaIndex]
        val source = suite.mediaSelectorTestBuilder.delayedMediaSource("switch-$mediaIndex")
        source.complete(listOf(media))
        suite.setMediaDuration(durationMillis)
        state.mediaSelectorFlow.filterNotNull().first().select(media)
        advanceUntilIdle()
    }

    private fun EpisodePlayerTestSuite.enableAutoPlayNext() {
        registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
    }

    private fun TestScope.createCase(
        getNextEpisode: suspend (currentEpisodeId: Int) -> Int?,
        resolver: MediaResolver = TestUniversalMediaResolver,
    ) = run {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.enableAutoPlayNext()
        suite.registerComponent<MediaResolver> { resolver }

        val state = suite.createState(
            listOf(
                SwitchNextEpisodeExtension.Factory(getNextEpisode = getNextEpisode),
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        Triple(testScope, suite, state)
    }

    @Test
    fun `does not switch if player does not finish`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `does not switch if position is not close to the end`() = runTest {

        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        loadSelectedMedia(suite, state, durationMillis = null)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        suite.player.injectPosition(95_000)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `can switch to next state normally`() = runTest {
        val (testScope, suite, state) =
            createCase(getNextEpisode = { 1000 })

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())

        testScope.cancel()
    }

    @Test
    fun `switches only once`() = runTest {
        var getNextEpisodeCalled = 0
        val (testScope, suite, state) =
            createCase(
                getNextEpisode = {
                    getNextEpisodeCalled++
                    1000
                },
            )

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        testScope.cancel()
    }

    @Test
    fun `getNextEpisode exception is caught`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope(this)
        val suite = EpisodePlayerTestSuite(this, scope)
        suite.enableAutoPlayNext()
        suite.registerComponent<MediaResolver> { TestUniversalMediaResolver }
        val state = suite.createState(
            listOf(
                SwitchNextEpisodeExtension.Factory(
                    getNextEpisode = {
                        throw RepositoryNetworkException()
                    },
                ),
            ),
        )
        state.onUIReady()
        advanceUntilIdle()

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()

        advanceUntilIdle()

        assertEquals(2, state.getCurrentEpisodeId())
        backgroundException.await().run {
            assertIs<ExtensionException>(this)
            assertIs<RepositoryNetworkException>(cause)
        }
        scope.cancel()
    }

    @Test
    fun `does not switch next episode when playback never started after switching`() = runTest {
        var getNextEpisodeCalled = 0
        var resolveCalls = 0

        val failingAfterFirstResolver = object : MediaResolver {
            override fun supports(media: Media): Boolean = true
            override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
                if (resolveCalls++ == 0) return TestUniversalMediaResolver.resolve(media, episode)
                throw UnsupportedMediaException(media)
            }
        }
        val (testScope, suite, state) =
            createCase(
                getNextEpisode = {
                    getNextEpisodeCalled++
                    1000
                },
                resolver = failingAfterFirstResolver,
            )

        loadSelectedMedia(suite, state)

        assertEquals(initialEpisodeId, state.getCurrentEpisodeId())
        assertEquals(0, getNextEpisodeCalled)

        suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
        advanceUntilIdle()
        suite.player.injectEnded()
        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        state.mediaSelectorFlow.filterNotNull().first().select(TestMediaList[0])
        advanceUntilIdle()

        suite.player.injectEnded()
        advanceUntilIdle()

        assertEquals(1000, state.getCurrentEpisodeId())
        assertEquals(1, getNextEpisodeCalled)

        testScope.cancel()
    }
}
