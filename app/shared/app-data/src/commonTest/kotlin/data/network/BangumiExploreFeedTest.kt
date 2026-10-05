/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.repository.RepositoryRequestError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BangumiExploreFeedTest {
    @Test
    fun `trending repository caches the first page and keeps fallback titles`() = runTest {
        val source = FakeExploreDataSource(
            trending = BangumiTrendingPage(
                subjects = listOf(
                    BangumiExploreSubject(
                        id = 42,
                        name = "Original title",
                        nameCn = "",
                        imageLarge = "",
                    ),
                ),
                total = 1,
            ),
        )
        val repository = TrendsRepository(source, ioDispatcher = Dispatchers.Default)

        val first = repository.getTrendsInfo()
        val second = repository.getTrendsInfo()

        assertEquals(1, source.trendingRequests)
        assertEquals("Original title", first.subjects.single().nameCn)
        assertEquals(first, second)
    }

    @Test
    fun `local recommendation ranking removes watched duplicates and hides nsfw`() {
        val candidates = listOf(
            BangumiRecommendationCandidate(
                id = 1,
                name = "watched",
                nameCn = "watched",
                imageLarge = "",
                tags = listOf("action"),
            ),
            BangumiRecommendationCandidate(
                id = 2,
                name = "tag match",
                nameCn = "tag match",
                imageLarge = "",
                score = 8.0,
                scoreCount = 100,
                tags = listOf("action"),
            ),
            BangumiRecommendationCandidate(
                id = 2,
                name = "duplicate",
                nameCn = "duplicate",
                imageLarge = "",
            ),
            BangumiRecommendationCandidate(
                id = 3,
                name = "nsfw",
                nameCn = "nsfw",
                imageLarge = "",
                nsfw = true,
            ),
        )

        val ranked = rankBangumiRecommendations(
            candidates = candidates,
            preferences = listOf(
                BangumiCollectionPreference(
                    subjectId = 1,
                    collectionType = 2,
                    tags = listOf("action"),
                    subjectTags = emptyList(),
                    score = 8,
                    nsfw = false,
                ),
            ),
            nsfwMode = NsfwMode.HIDE,
            limit = 10,
        )

        assertEquals(listOf(2), ranked.map { it.id })
        assertTrue(ranked.none { it.nsfw })
    }

    @Test
    fun `schedule assembly maps Bangumi dates to unknown local midnight`() {
        val today = LocalDate(2026, 10, 5)
        val tomorrow = today.plus(DatePeriod(days = 1))
        val schedule = assembleBangumiSchedule(
            dates = listOf(today, tomorrow),
            timeZone = TimeZone.of("Asia/Taipei"),
            calendar = listOf(
                BangumiCalendarEntry(
                    id = 100,
                    name = "Original",
                    nameCn = "中文",
                    imageLarge = "image",
                ),
            ),
            episodesBySubject = mapOf(
                100 to listOf(
                    BangumiExploreEpisode(
                        id = 1001,
                        type = 0,
                        name = "Episode",
                        nameCn = "第 1 集",
                        sort = "1",
                        ep = "1",
                        airDate = tomorrow,
                    ),
                ),
            ),
        )

        assertEquals(listOf(today, tomorrow), schedule.map { it.date })
        val item = schedule.last().list.single()
        assertEquals(100, item.subject.subjectId)
        assertEquals(1001, item.episode.episodeId)
        assertFalse(item.timeKnown)
        assertEquals(tomorrow.year, item.episode.airDate.year)
        assertEquals(tomorrow.monthNumber, item.episode.airDate.month)
        assertEquals(tomorrow.dayOfMonth, item.episode.airDate.day)
    }

    @Test
    fun `schedule service caches calendar and episode requests`() = runTest {
        val today = LocalDate(2026, 10, 5)
        val source = FakeExploreDataSource(
            calendar = listOf(
                BangumiCalendarEntry(100, "Original", "中文", ""),
            ),
            episodes = mapOf(
                100 to listOf(
                    BangumiExploreEpisode(1001, 0, "Episode", "", "1", "1", today),
                ),
            ),
        )
        val service = BangumiScheduleService(source, ioDispatcher = Dispatchers.Default)

        service.getRecentAiringSchedules(today, TimeZone.of("Asia/Taipei"))
        service.getRecentAiringSchedules(today, TimeZone.of("Asia/Taipei"))

        assertEquals(1, source.calendarRequests)
        assertEquals(1, source.episodeRequests)
    }

    @Test
    fun `schedule service exposes request failures as repository errors`() = runTest {
        val source = FakeExploreDataSource(
            calendarFailure = BangumiExploreRequestException(
                operation = "calendar",
                endpoint = "https://api.bgm.tv/calendar",
                statusCode = 503,
                message = "service unavailable",
            ),
        )
        val service = BangumiScheduleService(source, ioDispatcher = Dispatchers.Default)

        assertFailsWith<RepositoryRequestError> {
            service.getRecentAiringSchedules(
                today = LocalDate(2026, 10, 5),
                timeZone = TimeZone.of("Asia/Taipei"),
            )
        }
    }

    private class FakeExploreDataSource(
        private val trending: BangumiTrendingPage = BangumiTrendingPage(emptyList(), 0),
        private val calendar: List<BangumiCalendarEntry> = emptyList(),
        private val episodes: Map<Int, List<BangumiExploreEpisode>> = emptyMap(),
        private val calendarFailure: Throwable? = null,
    ) : BangumiExploreDataSource {
        var trendingRequests = 0
        var calendarRequests = 0
        var episodeRequests = 0

        override suspend fun getTrendingSubjects(limit: Int, offset: Int): BangumiTrendingPage {
            trendingRequests++
            return trending
        }

        override suspend fun getCalendar(): List<BangumiCalendarEntry> {
            calendarRequests++
            calendarFailure?.let { throw it }
            return calendar
        }

        override suspend fun getEpisodes(subjectId: Int): List<BangumiExploreEpisode> {
            episodeRequests++
            return episodes[subjectId].orEmpty()
        }

        override suspend fun getCollectionPreferences(): List<BangumiCollectionPreference> = emptyList()
    }
}
