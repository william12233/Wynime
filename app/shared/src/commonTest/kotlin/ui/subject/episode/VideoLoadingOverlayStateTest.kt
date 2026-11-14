package com.wynime.app.ui.subject.episode

import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.ui.subject.episode.video.loading.shouldShowVideoLoadingIndicator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoLoadingOverlayStateTest {
    @Test
    fun `initial player state does not show source search overlay`() {
        assertEquals(
            VideoLoadingState.Initial,
            playerOverlayVideoLoadingState(
                VideoLoadingState.Initial,
                autoSelectInProgress = false,
                onlineSourceMode = false,
            ),
        )
        assertFalse(shouldShowVideoLoadingIndicator(VideoLoadingState.Initial, buffering = false, playerError = false))
    }

    @Test
    fun `active online auto select has a distinct visible state`() {
        val state = playerOverlayVideoLoadingState(
            VideoLoadingState.Initial,
            autoSelectInProgress = true,
            onlineSourceMode = true,
        )

        assertEquals(VideoLoadingState.AutoSelectingSource, state)
        assertTrue(shouldShowVideoLoadingIndicator(state, buffering = false, playerError = false))
    }

    @Test
    fun `local playback suppresses online auto select text`() {
        assertEquals(
            VideoLoadingState.Initial,
            playerOverlayVideoLoadingState(
                VideoLoadingState.Initial,
                autoSelectInProgress = true,
                onlineSourceMode = false,
            ),
        )
        assertFalse(shouldShowVideoLoadingIndicator(VideoLoadingState.Initial, buffering = false, playerError = false))
    }

    @Test
    fun `initial state still surfaces actual player buffering and errors`() {
        assertTrue(shouldShowVideoLoadingIndicator(VideoLoadingState.Initial, buffering = true, playerError = false))
        assertTrue(shouldShowVideoLoadingIndicator(VideoLoadingState.Initial, buffering = false, playerError = true))
    }
}
