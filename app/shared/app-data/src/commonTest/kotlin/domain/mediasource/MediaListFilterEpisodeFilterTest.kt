package com.wynime.app.domain.mediasource

import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.test.DynamicTestsBuilder
import com.wynime.test.TestContainer
import com.wynime.test.TestFactory
import com.wynime.test.runDynamicTests
import kotlin.test.assertEquals

@TestContainer
class MediaListFilterEpisodeFilterTest {

    @TestFactory
    fun `base cases`() = runDynamicTests {
        case(
            "simple sort match",
            subjectName = "来自深渊 烈日的黄金乡",
            episodeSort = EpisodeSort(1),
            episodeName = "测试",
            mediaEpisodeSort = EpisodeSort(1),
            expected = true,
        )
        case(
            "simple sort not match",
            subjectName = "来自深渊 烈日的黄金乡",
            episodeSort = EpisodeSort(1),
            episodeName = "测试",
            mediaEpisodeSort = EpisodeSort(2),
            expected = false,
        )
        case(
            "simple ep match",
            subjectName = "来自深渊 烈日的黄金乡",
            episodeSort = EpisodeSort(13),
            episodeEp = EpisodeSort(1),
            episodeName = "测试",
            mediaEpisodeSort = EpisodeSort(1),
            expected = true,
        )
        case(
            "both ep and sort does not match",
            subjectName = "来自深渊 烈日的黄金乡",
            episodeSort = EpisodeSort(13),
            episodeEp = EpisodeSort(1),
            episodeName = "测试",
            mediaEpisodeSort = EpisodeSort(2),
            expected = false,
        )
    }

    @TestFactory
    fun `match episode name`() = runDynamicTests {
        case(
            "match 电影版",
            subjectName = "来自深渊",
            episodeSort = EpisodeSort("电影版"),
            episodeName = "电影版",
            mediaEpisodeSort = null,
            mediaTitle = "来自深渊 电影版",
            expected = true,
        )
        case(
            "match OVA",
            subjectName = "来自深渊",
            episodeSort = EpisodeSort("OVA"),
            episodeName = "OVA",
            mediaEpisodeSort = null,
            mediaTitle = "来自深渊 OVA",
            expected = true,
        )
        case(
            "match episode name - control",
            subjectName = "来自深渊",
            episodeSort = EpisodeSort(1),
            episodeName = "电影版",
            mediaEpisodeSort = null,
            mediaTitle = "来自深渊 01",
            expected = false,
        )
    }

    @TestFactory
    fun `subjectName contains episodeName`() = runDynamicTests {
        case(
            "true case",
            subjectName = "来自深渊 烈日的黄金乡",
            episodeSort = EpisodeSort(12),
            episodeName = "黄金",
            mediaEpisodeSort = EpisodeSort(12),
            expected = true,
        )
        case(
            "fail case",
            subjectName = "来自深渊 烈日的黄金乡",
            episodeSort = EpisodeSort(11),
            episodeName = "黄金",
            mediaEpisodeSort = EpisodeSort(12),
            expected = false,
        )
    }

    private fun DynamicTestsBuilder.case(
        testName: String,
        subjectName: String,
        episodeSort: EpisodeSort,
        episodeName: String?,
        expected: Boolean,
        mediaEpisodeSort: EpisodeSort?,
        mediaTitle: String = "$subjectName $mediaEpisodeSort",
        episodeEp: EpisodeSort? = null,
    ) {
        add(testName) {
            val context = MediaListFilterContext(
                subjectNames = setOf(subjectName),
                episodeSort = episodeSort,
                episodeEp = episodeEp,
                episodeName = episodeName,
            )
            context.run {
                assertEquals(
                    expected,
                    MediaListFilters.ContainsAnyEpisodeInfo.applyOn(
                        object : MediaListFilter.Candidate {
                            override val originalTitle: String get() = mediaTitle
                            override val subjectName: String get() = subjectName
                            override val episodeRange: EpisodeRange?
                                get() = mediaEpisodeSort?.let {
                                    EpisodeRange.single(it)
                                }
                        },
                    ),
                )
            }
        }
    }
}