package com.wynime.app.ui.download.components

import kotlin.test.Test
import kotlin.test.assertEquals
import com.wynime.app.data.models.player.EpisodeHistory
import com.wynime.app.tools.Progress

class DownloadRowTest {
    @Test
    fun `playback history is converted to clamped progress`() {
        val history = EpisodeHistory(
            episodeId = 1,
            positionMillis = 30_000,
            durationMillis = 60_000,
        )

        assertEquals(0.5f, history.toPlaybackProgress().getOrNull())
        assertEquals(1f, history.copy(positionMillis = 90_000).toPlaybackProgress().getOrNull())
    }

    @Test
    fun `invalid playback history has unspecified progress`() {
        val history = EpisodeHistory(
            episodeId = 1,
            positionMillis = 30_000,
            durationMillis = 60_000,
        )

        assertEquals(Progress.Unspecified, null.toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(positionMillis = 0).toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(durationMillis = null).toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(durationMillis = 0).toPlaybackProgress())
        assertEquals(Progress.Unspecified, history.copy(deletedAtMillis = 1).toPlaybackProgress())
    }
}
