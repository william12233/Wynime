package com.wynime.app.ui.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import com.wynime.app.ui.framework.runComposeStateTest
import com.wynime.app.ui.framework.takeSnapshot
import com.wynime.app.videoplayer.ui.progress.PlayerProgressSliderState
import org.openani.mediamp.metadata.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerProgressSliderStateTest {

    @Test
    fun `preview and finish preview then play`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = { chapters },
            onPreview = {},
            onPreviewFinished = {
                positionState = it
            },
        )

        state.previewPositionRatio(0.5f)
        state.finishPreview()
        takeSnapshot()
        assertEquals(0.5f, state.displayPositionRatio)
        positionState += 5_000L
        takeSnapshot()
        assertEquals(0.55f, state.displayPositionRatio)
    }

    @Test
    fun `preview and play 5s and finish preview then play`() = runComposeStateTest {
        val chapters = persistentListOf<Chapter>()
        var positionState by mutableLongStateOf(0L)

        val state = PlayerProgressSliderState(
            currentPositionMillis = { positionState },
            totalDurationMillis = { 100_000L },
            chapters = { chapters },
            onPreview = {},
            onPreviewFinished = {
                positionState = it
            },
        )

        state.previewPositionRatio(0.5f)
        positionState += 5_000L
        takeSnapshot()
        assertEquals(0.5f, state.displayPositionRatio)
        state.finishPreview()
        takeSnapshot()
        assertEquals(0.5f, state.displayPositionRatio)
        positionState += 5_000L
        takeSnapshot()
        assertEquals(0.55f, state.displayPositionRatio)
    }
}