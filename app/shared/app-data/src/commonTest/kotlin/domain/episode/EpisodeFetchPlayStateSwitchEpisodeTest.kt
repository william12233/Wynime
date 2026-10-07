@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import com.wynime.app.data.repository.player.EpisodeHistories
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.TestUniversalMediaResolver
import com.wynime.app.domain.player.extension.AbstractPlayerExtensionTest
import com.wynime.app.domain.player.extension.EpisodePlayerExtensionFactory
import com.wynime.app.domain.player.extension.RememberPlayProgressExtension
import com.wynime.app.domain.player.extension.SwitchNextEpisodeExtension
import com.wynime.app.domain.player.extension.loadMedia
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.utils.coroutines.childScope
import kotlin.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeFetchPlayStateSwitchEpisodeTest : AbstractPlayerExtensionTest() {
    private val playHistory = EpisodePlayHistoryRepositoryImpl(
        MemoryDataStore(EpisodeHistories.Empty),
        createMemoryPlaybackHistoryDao(),
        nowMillis = { 0 },
    )
    private val newEpisodeId = 1000

    private fun TestScope.createCase(): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)
        suite.registerComponent<EpisodePlayHistoryRepository> { playHistory }
        suite.registerComponent<GetVideoScaffoldConfigUseCase> {
            GetVideoScaffoldConfigUseCase {
                flowOf(VideoScaffoldConfig.AllDisabled.copy(autoPlayNext = true))
            }
        }
        suite.registerComponent<MediaResolver> {
            TestUniversalMediaResolver
        }

        val rememberPlayProgress = EpisodePlayerExtensionFactory { context, koin ->
            RememberPlayProgressExtension(
                context,
                koin,
                periodicReportInterval = Duration.INFINITE,
            )
        }
        val state = suite.createState(
            listOf(
                rememberPlayProgress,
                SwitchNextEpisodeExtension.Factory(
                    getNextEpisode = { currentEpisodeId ->
                        assertEquals(initialEpisodeId, currentEpisodeId)
                        newEpisodeId
                    },
                ),
            ),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    @Test
    fun `switchEpisode then load play history - no media source`() = runTest {
        val (testScope, suite, state) =
            createCase()
        try {
            playHistory.saveOrUpdate(initialEpisodeId, 3000)
            playHistory.saveOrUpdate(newEpisodeId, 5000)

            assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

            suite.player.loadMedia(durationMs = 100_000, playWhenReady = false)
            advanceUntilIdle()

            suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
            advanceUntilIdle()
            suite.player.injectEnded()
            advanceUntilIdle()

            assertEquals(3000, playHistory.getPositionMillisByEpisodeId(initialEpisodeId))

            assertEquals(initialEpisodeId, state.getCurrentEpisodeId())
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `switchEpisode then load play history - wait for media source`() = runTest {
        val (testScope, suite, state) =
            createCase()
        try {
            val ms1 = suite.mediaSelectorTestBuilder.delayedMediaSource("1")

            playHistory.saveOrUpdate(initialEpisodeId, 3000)
            playHistory.saveOrUpdate(newEpisodeId, 5000)

            assertEquals(initialEpisodeId, state.getCurrentEpisodeId())

            val myMedia = TestMediaList[0]
            ms1.complete(listOf(myMedia))
            suite.setMediaDuration(100_000)
            state.mediaSelectorFlow.filterNotNull().first().select(myMedia)
            advanceUntilIdle()

            assertEquals(3000, suite.player.currentPositionMillis.value)

            suite.player.seekTo(suite.player.mediaProperties.value!!.durationMillis!!)
            advanceUntilIdle()
            suite.player.injectEnded()
            advanceUntilIdle()

            assertEquals(null, playHistory.getPositionMillisByEpisodeId(initialEpisodeId))

            assertEquals(newEpisodeId, state.getCurrentEpisodeId())

            val myMedia1 = TestMediaList[1]
            ms1.complete(listOf(myMedia1))
            suite.setMediaDuration(100_000)
            state.mediaSelectorFlow.filterNotNull().first().select(myMedia1)
            advanceUntilIdle()

            assertEquals(5000, suite.player.currentPositionMillis.value)
        } finally {
            testScope.cancel()
        }
    }
}
