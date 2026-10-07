package com.wynime.app.domain.media.fetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.source.MediaFetchRequest

class MediaFetchRequestEpisodeNumbersTest {
    private val episode = EpisodeInfo(episodeId = 10, type = EpisodeType.MainStory, sort = EpisodeSort(1), ep = EpisodeSort(1))
    private val other = EpisodeInfo(episodeId = 11, type = EpisodeType.MainStory, sort = EpisodeSort(2), ep = EpisodeSort(2))

    private fun request(episodeId: Int, sort: Int) = MediaFetchRequest(
        subjectId = "1",
        episodeId = episodeId.toString(),
        subjectNames = listOf("A"),
        episodeSort = EpisodeSort(sort),
        episodeName = "",
        episodeEp = EpisodeSort(sort),
    )

    @Test
    fun `requested numbers apply only to the episode the request points at`() {
        val edited = episode.withRequestedNumbers(request(10, sort = 5))
        assertEquals(EpisodeSort(5), edited.sort)
        assertEquals(EpisodeSort(5), edited.ep)
        assertSame(other, other.withRequestedNumbers(request(10, sort = 5)))
        assertSame(episode, episode.withRequestedNumbers(request(10, sort = 1)))
    }
}
