package com.wynime.app.domain.episode

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.player.extension.AbstractPlayerExtensionTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeFetchSelectPlayStateTest : AbstractPlayerExtensionTest() {

    @Test
    fun `can create state`() = runTest {
        val suite = createSuite()
        val state = suite.createState()
        assertEquals(subjectId, state.subjectId)
    }

    private fun TestScope.createSuite() = EpisodePlayerTestSuite(this, backgroundScope)
}

