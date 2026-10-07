package com.wynime.app.videoplayer.ui.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlayerProgressSliderStateTest {
    @Test
    fun `cancel preview does not finish seek`() {
        val finishedPositions = mutableListOf<Long>()
        val state = PlayerProgressSliderState(
            currentPositionMillis = { 10_000L },
            totalDurationMillis = { 100_000L },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = finishedPositions::add,
        )

        state.previewPositionRatio(0.5f)
        state.cancelPreview()

        assertFalse(state.isPreviewing)
        assertEquals(emptyList(), finishedPositions)
        assertEquals(0.1f, state.displayPositionRatio)
    }
}
