@file:OptIn(UnsafeEpisodeSessionApi::class)

package com.wynime.app.domain.episode

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.player.extension.AbstractPlayerExtensionTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EpisodeFetchSelectPlayStateTest : AbstractPlayerExtensionTest() {

    @Test
    fun `can create state`() = runTest {
        val suite = createSuite()
        val state = suite.createState()
        assertEquals(subjectId, state.subjectId)
    }

    @Test
    fun `does not create remote media fetch until explicitly enabled`() = runTest {
        val suite = createSuite()
        val state = EpisodeFetchSelectPlayState(
            subjectId = subjectId,
            initialEpisodeId = initialEpisodeId,
            player = suite.player,
            backgroundScope = backgroundScope,
            extensions = emptyList(),
            koin = suite.koin,
            mainDispatcher = kotlin.coroutines.EmptyCoroutineContext,
            mediaFetchInitiallyEnabled = false,
        )
        state.onUIReady()

        assertNull(state.episodeSessionFlow.value.fetchSelectFlow.first())
        state.enableMediaFetch()

        assertNotNull(state.mediaSelectorFlow.filterNotNull().first())
        advanceUntilIdle()
    }

    private fun TestScope.createSuite() = EpisodePlayerTestSuite(this, backgroundScope)
}

