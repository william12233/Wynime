package com.wynime.app.data.models.player

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeHistoryProgressTest {
    @Test
    fun `progress is position over duration clamped to one`() {
        assertEquals(0.25f, EpisodeHistory(episodeId = 1, positionMillis = 250, durationMillis = 1000).playProgress)
        assertEquals(1f, EpisodeHistory(episodeId = 1, positionMillis = 1500, durationMillis = 1000).playProgress)
    }

    @Test
    fun `progress is null without a usable duration`() {
        assertEquals(null, EpisodeHistory(episodeId = 1, positionMillis = 250).playProgress)
        assertEquals(null, EpisodeHistory(episodeId = 1, positionMillis = 250, durationMillis = 0).playProgress)
    }

    @Test
    fun `map keeps only records with positive computable progress`() {
        val map = listOf(
            EpisodeHistory(episodeId = 1, positionMillis = 250, durationMillis = 1000),
            EpisodeHistory(episodeId = 2, positionMillis = 0, durationMillis = 1000),
            EpisodeHistory(episodeId = 3, positionMillis = 500),
        ).playProgressByEpisodeId()
        assertEquals(mapOf(1 to 0.25f), map)
    }
}
