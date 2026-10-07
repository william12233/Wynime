package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import com.wynime.utils.coroutines.childScope
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSpeedExtensionTest : AbstractPlayerExtensionTest() {
    private val newEpisodeId = 3

    private fun TestScope.createCase(
        playbackSpeedFlow: MutableStateFlow<Float>,
    ): Triple<CoroutineScope, EpisodePlayerTestSuite, EpisodeFetchSelectPlayState> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val testScope = this.childScope()
        val suite = EpisodePlayerTestSuite(this, testScope)

        val state = suite.createState(
            extensions = listOf(PlaybackSpeedExtension.Factory(playbackSpeedFlow)),
        )
        state.onUIReady()
        advanceUntilIdle()
        return Triple(testScope, suite, state)
    }

    private val EpisodePlayerTestSuite.playerSpeed: Float?
        get() = player.features[PlaybackSpeed]?.value

    @Test
    fun `reapplies the speed after switching episode`() = runTest {
        val speed = MutableStateFlow(1f)
        val (testScope, suite, state) = createCase(speed)
        try {
            speed.value = 1.75f
            advanceUntilIdle()
            assertEquals(1.75f, suite.playerSpeed)

            suite.player.features[PlaybackSpeed]?.set(1f)
            state.switchEpisode(newEpisodeId)
            advanceUntilIdle()

            assertEquals(1.75f, suite.playerSpeed)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `reapplies the speed when media becomes loaded`() = runTest {
        val speed = MutableStateFlow(1.75f)
        val (testScope, suite, _) = createCase(speed)
        try {
            advanceUntilIdle()
            assertEquals(1.75f, suite.playerSpeed)

            suite.player.loadMedia(100_000L)
            suite.player.features[PlaybackSpeed]?.set(1f)
            suite.setMediaDuration(100_000L)
            advanceUntilIdle()

            assertEquals(1.75f, suite.playerSpeed)
        } finally {
            testScope.cancel()
        }
    }
}
