package com.wynime.app.videoplayer.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSpeedControlsTest {
    private val range = 0.5f..2.5f

    @Test
    fun `keyboard adjustment adds or subtracts one step`() {
        assertEquals(1.5f, nextPlaybackSpeed(1.3f, range, 1))
        assertEquals(1f, nextPlaybackSpeed(1.3f, range, -1))
    }

    @Test
    fun `preview speed immediately updates the shared UI state`() = runTest {
        val state = PlaybackSpeedControllerState(
            NoOpPlaybackSpeedController,
            scope = backgroundScope,
        )

        state.previewSpeed(1.75f)

        assertEquals(1.75f, state.currentSpeed)
    }
}
