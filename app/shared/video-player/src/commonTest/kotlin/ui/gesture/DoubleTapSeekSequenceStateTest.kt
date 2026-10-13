package com.wynime.app.videoplayer.ui.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

class DoubleTapSeekSequenceStateTest {
    @Test
    fun `repeated double taps increase multiplier`() {
        val state = DoubleTapSeekSequenceState()

        assertEquals(1, state.nextMultiplier(SkipDirection.FORWARD))
        assertEquals(2, state.nextMultiplier(SkipDirection.FORWARD))
        assertEquals(3, state.nextMultiplier(SkipDirection.FORWARD))
    }

    @Test
    fun `changing direction starts a new sequence`() {
        val state = DoubleTapSeekSequenceState()

        state.nextMultiplier(SkipDirection.BACKWARD)
        state.nextMultiplier(SkipDirection.BACKWARD)

        assertEquals(1, state.nextMultiplier(SkipDirection.FORWARD))
    }

    @Test
    fun `reset starts a new sequence`() {
        val state = DoubleTapSeekSequenceState()

        state.nextMultiplier(SkipDirection.FORWARD)
        state.nextMultiplier(SkipDirection.FORWARD)
        state.reset()

        assertEquals(1, state.nextMultiplier(SkipDirection.FORWARD))
    }
}
