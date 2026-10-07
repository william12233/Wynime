package com.wynime.app.videoplayer.ui.gesture

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureIndicatorStateTest {
    @Test
    fun `fresh state has no indicator presentation`() {
        assertNull(gestureIndicatorPresentation(GestureIndicatorState(), null))
    }

    @Test
    fun `playback speed is shown temporarily`() = runTest {
        val state = GestureIndicatorState()

        launch { state.showPlaybackSpeed(1.25f) }
        runCurrent()

        assertTrue(state.visible)
        assertEquals(GestureIndicatorState.State.PLAYBACK_SPEED, state.state)
        assertEquals(1.25f, state.playbackSpeed)

        advanceUntilIdle()
        assertFalse(state.visible)
    }

    @Test
    fun `seek cancellation remains visible until stopped`() {
        val state = GestureIndicatorState()

        val ticket = state.startSeekCancellation()
        assertTrue(state.visible)
        assertTrue(state.seekCancelled)

        state.stopSeekCancellation(ticket)
        assertFalse(state.visible)
    }

    @Test
    fun `showing regular seek clears cancellation state`() = runTest {
        val state = GestureIndicatorState()
        state.startSeekCancellation()

        state.showSeeking(deltaSeconds = 10)

        assertFalse(state.seekCancelled)
    }
}
