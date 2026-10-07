package com.wynime.app.videoplayer.ui.progress

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.videoplayer.ui.NoOpPlaybackSpeedController
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaProgressIndicatorTextUiTest {
    @Test
    fun `switching back to one times speed restores compact layout width`() = runWynimeComposeUiTest {
        val progressState = PlayerProgressSliderState(
            currentPositionMillis = { 0L },
            totalDurationMillis = { 100_000L },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = {},
        )
        var layoutWidth = 0
        lateinit var playbackSpeedState: PlaybackSpeedControllerState

        setContent {
            val scope = rememberCoroutineScope()
            playbackSpeedState = remember {
                PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
            }
            MediaProgressIndicatorText(
                progressState,
                modifier = Modifier.onSizeChanged { layoutWidth = it.width },
                playbackSpeedState = playbackSpeedState,
            )
        }

        var compactWidth = 0
        runOnIdle { compactWidth = layoutWidth }

        runOnIdle { playbackSpeedState.previewSpeed(2f) }
        runOnIdle {
            assertTrue(layoutWidth > compactWidth, "remaining time mode should expand the layout")
        }

        runOnIdle { playbackSpeedState.previewSpeed(1f) }
        runOnIdle {
            assertEquals(compactWidth, layoutWidth)
        }
    }

    @Test
    fun `preview started before player initialization observes initialized duration and speed`() = runWynimeComposeUiTest {
        val durationMillis = mutableLongStateOf(0L)
        val state = PlayerProgressSliderState(
            currentPositionMillis = { 0L },
            totalDurationMillis = { durationMillis.longValue },
            chapters = { emptyList() },
            onPreview = {},
            onPreviewFinished = {},
        )
        lateinit var playbackSpeedState: PlaybackSpeedControllerState

        setContent {
            val scope = rememberCoroutineScope()
            playbackSpeedState = remember {
                PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
            }
            MediaProgressIndicatorText(state, playbackSpeedState = playbackSpeedState)
        }

        runOnIdle {
            state.previewPositionRatio(0.25f)
            playbackSpeedState.previewSpeed(2f)
        }
        onNodeWithTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT)
            .assertTextEquals("00:00 / 00:00")

        runOnIdle {
            durationMillis.longValue = 10 * 60 * 1000L
        }

        onNodeWithTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT)
            .assertTextEquals("02:30 / 10:00 (-03:45)")
    }
}
