package com.wynime.app.domain.media.download

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.EpisodeRange

class DownloadReusePolicyTest {

    private val episode = requestTestEpisode(14).copy(ep = EpisodeSort(2))
    private val season = requestTestMedia(1, EpisodeRange.range(EpisodeSort(1), EpisodeSort(12)))
    private val single = requestTestMedia(2, EpisodeRange.single(EpisodeSort(2)))
    private val unknown = requestTestMedia(3, null)

    @Test
    fun `season range covering ep is reused`() {
        assertSame(season, BatchDownloadPlanner.findReusableSeasonMedia(episode, listOf(single, unknown, season)))
    }

    @Test
    fun `season range covering sort is reused`() {
        assertSame(season, BatchDownloadPlanner.findReusableSeasonMedia(episode.copy(sort = EpisodeSort(2), ep = null), listOf(season)))
    }

    @Test
    fun `season range covering neither sort nor ep is not reused`() {
        assertNull(BatchDownloadPlanner.findReusableSeasonMedia(episode.copy(ep = null), listOf(season)))
    }

    @Test
    fun `whole season pack covers every episode`() {
        val pack = requestTestMedia(4, EpisodeRange.season(1))
        assertSame(pack, BatchDownloadPlanner.findReusableSeasonMedia(episode.copy(ep = null), listOf(pack)))
    }

    @Test
    fun `single episode media is never reused`() {
        val degenerateRange = requestTestMedia(5, EpisodeRange.range(EpisodeSort(2), EpisodeSort(2)))
        assertNull(BatchDownloadPlanner.findReusableSeasonMedia(episode, listOf(single, degenerateRange, unknown)))
        assertNull(BatchDownloadPlanner.findReusableSeasonMedia(episode, emptyList()))
    }

    @Test
    fun `cached media is unwrapped to its origin and the first match wins`() {
        val cached = requestTestCache(season, subjectId = 1, episodeId = 1).media
        val later = requestTestMedia(6, EpisodeRange.range(EpisodeSort(1), EpisodeSort(24)))
        assertSame(season, BatchDownloadPlanner.findReusableSeasonMedia(episode, listOf(single, cached, later)))
    }
}
