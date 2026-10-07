package com.wynime.app.ui.exploration.schedule

import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeWithAiringTimeDefaultsTest {
    @Test
    fun `renderEpisodeDisplay sort only`() {
        assertEquals("第 1 话", renderEpisodeDisplay(EpisodeSort(1), null, null))
    }

    @Test
    fun `renderEpisodeDisplay sort with name`() {
        assertEquals("第 1 话  Foo", renderEpisodeDisplay(EpisodeSort(1), null, "Foo"))
    }

    @Test
    fun `renderEpisodeDisplay sort equal ep`() {
        assertEquals("第 1 话", renderEpisodeDisplay(EpisodeSort(1), EpisodeSort(1), null))
    }

    @Test
    fun `renderEpisodeDisplay sort equal ep with name`() {
        assertEquals("第 1 话  Foo", renderEpisodeDisplay(EpisodeSort(1), EpisodeSort(1), "Foo"))
    }

    @Test
    fun `renderEpisodeDisplay sort does not equal ep`() {
        assertEquals("第 1 (12) 话", renderEpisodeDisplay(EpisodeSort(12), EpisodeSort(1), null))
    }

    @Test
    fun `renderEpisodeDisplay sort does not equal ep with name`() {
        assertEquals("第 1 (12) 话  Foo", renderEpisodeDisplay(EpisodeSort(12), EpisodeSort(1), "Foo"))
    }

    @Test
    fun `renderEpisodeDisplay special sort`() {
        assertEquals(
            "OVA01  Foo",
            renderEpisodeDisplay(
                EpisodeSort(1, EpisodeType.OVA),
                EpisodeSort(1, EpisodeType.OVA),
                "Foo",
            ),
        )
    }

    @Test
    fun `renderEpisodeDisplay unknown sort`() {
        assertEquals(
            "剧场版  Foo",
            renderEpisodeDisplay(
                EpisodeSort("剧场版"),
                EpisodeSort("剧场版"),
                "Foo",
            ),
        )
    }
}

private fun renderEpisodeDisplay(
    episodeSort: EpisodeSort,
    episodeEp: EpisodeSort?,
    episodeName: String?
): String {
    val epText = episodeEp?.toString()?.removePrefix("0")
    val sortText = episodeSort.toString().removePrefix("0")

    val sortDisplay = if (episodeEp == null || episodeEp == episodeSort) {
        if (episodeSort is EpisodeSort.Normal) {
            "第 $sortText 话"
        } else {
            sortText
        }
    } else {
        check(epText != null)
        if (episodeSort is EpisodeSort.Normal && episodeEp is EpisodeSort.Normal) {
            "第 $epText ($sortText) 话"
        } else {
            "$epText ($sortText)"
        }
    }

    return if (episodeName == null) {
        sortDisplay
    } else {
        "$sortDisplay  $episodeName"
    }
}
