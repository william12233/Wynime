package com.wynime.app.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.repository.RepositoryRequestError
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
        assertEquals(listOf(50 to 0), source.trendingRequestParameters)
        assertEquals("Original title", first.subjects.single().nameCn)
        assertEquals(first, second)
    }

    @Test
    fun `calendar repository caches force refreshes and shares in flight request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeExploreDataSource(
            calendar = listOf(BangumiCalendarEntry(100, "Original", "中文", "")),
            calendarGate = gate,
        )
        val repository = BangumiCalendarRepository(
            dataSource = source,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val first = backgroundScope.async { repository.getCalendarDays() }
        source.calendarStarted.await()
        val second = backgroundScope.async { repository.getCalendarDays() }
        gate.complete(Unit)

        assertEquals(first.await(), second.await())
        assertEquals(1, source.calendarRequests)
        assertEquals(source.calendarDays, repository.getCalendarDays())
        assertEquals(1, source.calendarRequests)

        repository.getCalendarDays(forceRefresh = true)
        assertEquals(2, source.calendarRequests)
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
        private val calendarGate: CompletableDeferred<Unit>? = null,
    ) : BangumiExploreDataSource {
        var trendingRequests = 0
        val trendingRequestParameters = mutableListOf<Pair<Int, Int>>()
        var calendarRequests = 0
        var episodeRequests = 0
        val calendarStarted = CompletableDeferred<Unit>()
        val calendarDays = listOf(BangumiCalendarDay(weekdayId = 1, items = calendar))

        override suspend fun getTrendingSubjects(limit: Int, offset: Int): BangumiTrendingPage {
            trendingRequests++
            trendingRequestParameters += limit to offset
            return trending
        }

        override suspend fun getCalendarDays(): List<BangumiCalendarDay> {
            calendarRequests++
            calendarStarted.complete(Unit)
            calendarGate?.await()
            calendarFailure?.let { throw it }
            return calendarDays
        }

        override suspend fun getEpisodes(subjectId: Int): List<BangumiExploreEpisode> {
            episodeRequests++
            return episodes[subjectId].orEmpty()
        }

        override suspend fun getCollectionPreferences(): List<BangumiCollectionPreference> = emptyList()
    }
}
