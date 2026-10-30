package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class SteppedDraggableStateTest {
    @Test
    fun `each drag starts from a fresh step offset`() {
        val directions = mutableListOf<StepDirection>()
        val state = SteppedDraggableStateImpl(
            onStep = directions::add,
            stepSizePx = 10f,
        )

        state.onDragStarted(Offset(0f, 100f), Orientation.Vertical)
        state.dispatchRawDelta(-20f)
        state.onDragStopped(0f)

        state.onDragStarted(Offset(0f, 200f), Orientation.Vertical)
        state.dispatchRawDelta(-20f)
        state.onDragStopped(0f)

        assertEquals(
            listOf(StepDirection.FORWARD, StepDirection.FORWARD),
            directions,
        )
    }
}
