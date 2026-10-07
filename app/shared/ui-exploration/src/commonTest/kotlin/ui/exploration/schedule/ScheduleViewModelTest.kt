package com.wynime.app.ui.exploration.schedule

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import com.wynime.app.data.models.subject.LightEpisodeInfo
import com.wynime.app.data.models.subject.LightSubjectInfo
import com.wynime.app.domain.episode.AiringScheduleForDate
import com.wynime.app.domain.episode.EpisodeWithAiringTime
import com.wynime.app.domain.episode.GetAnimeScheduleFlowUseCase
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.UTC9
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

class ScheduleViewModelTest {

    @Test
    fun `delayUntilNextMidnight in a zone without DST`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = LocalDateTime(2026, 9, 4, 22, 30).toInstant(zone)
        assertEquals(1.hours + 30.minutes, ScheduleViewModel.delayUntilNextMidnight(now, zone))
    }

    @Test
    fun `delayUntilNextMidnight at exactly midnight is a full day`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = LocalDateTime(2026, 9, 5, 0, 0).toInstant(zone)
        assertEquals(24.hours, ScheduleViewModel.delayUntilNextMidnight(now, zone))
    }

    @Test
    fun `delayUntilNextMidnight one millisecond before midnight`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = LocalDateTime(2026, 9, 5, 0, 0).toInstant(zone) - 1.milliseconds
        assertEquals(1.milliseconds, ScheduleViewModel.delayUntilNextMidnight(now, zone))
    }

    @Test
    fun `delayUntilNextMidnight targets the next local midnight not UTC midnight`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = Instant.parse("2026-09-04T15:00:00Z")
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertEquals(1.hours, delay)
        assertEquals(LocalDate(2026, 9, 5).atStartOfDayIn(zone), now + delay)
    }

    @Test
    fun `delayUntilNextMidnight across DST spring forward`() {

        val zone = TimeZone.of("America/New_York")
        val now = LocalDateTime(2026, 3, 8, 1, 30).toInstant(zone)
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertEquals(21.hours + 30.minutes, delay)
        assertEquals(LocalDate(2026, 3, 9).atStartOfDayIn(zone), now + delay)
    }

    @Test
    fun `delayUntilNextMidnight across DST fall back`() {

        val zone = TimeZone.of("America/New_York")
        val now = Instant.parse("2026-11-01T05:30:00Z")
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertEquals(23.hours + 30.minutes, delay)
        assertEquals(LocalDate(2026, 11, 2).atStartOfDayIn(zone), now + delay)
    }

    @Test
    fun `delayUntilNextMidnight when the next midnight does not exist`() {

        val zone = TimeZone.of("America/Sao_Paulo")
        val now = LocalDateTime(2018, 11, 3, 12, 0).toInstant(zone)
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertTrue(delay.isPositive())
        assertEquals(LocalDate(2018, 11, 4).atStartOfDayIn(zone), now + delay)
        assertEquals(Instant.parse("2018-11-04T03:00:00Z"), now + delay)
    }

    @Test
    fun `delayUntilNextMinute is aligned to the minute and within one minute`() {
        assertEquals(1.minutes, ScheduleViewModel.delayUntilNextMinute(Instant.parse("2026-09-04T12:00:00Z")))
        assertEquals(30.seconds, ScheduleViewModel.delayUntilNextMinute(Instant.parse("2026-09-04T12:00:30Z")))
        assertEquals(1.milliseconds, ScheduleViewModel.delayUntilNextMinute(Instant.parse("2026-09-04T12:00:59.999Z")))
    }

    private class OffsetClock(private val start: Instant) : Clock {
        private val mark = TimeSource.Monotonic.markNow()
        override fun now(): Instant = start + mark.elapsedNow()
    }

    private class FakeGetAnimeScheduleFlowUseCase(
        private val handler: (today: LocalDate, callIndex: Int) -> Flow<List<AiringScheduleForDate>>,
    ) : GetAnimeScheduleFlowUseCase {
        val calls = MutableStateFlow<List<LocalDate>>(emptyList())

        override fun invoke(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>> {
            val index = calls.value.size
            calls.update { it + today }
            return handler(today, index)
        }
    }

    private val timeZone = TimeZone.of("Asia/Shanghai")

    private fun koinWith(useCase: GetAnimeScheduleFlowUseCase): Koin = koinApplication {
        modules(
            module {
                single<GetAnimeScheduleFlowUseCase> { useCase }
            },
        )
    }.koin

    private fun episode(
        subjectId: Int,
        airingTime: Instant,
        timeKnown: Boolean,
    ) = EpisodeWithAiringTime(
        subject = LightSubjectInfo(subjectId = subjectId, name = "Subject $subjectId", nameCn = "", imageLarge = ""),
        episode = LightEpisodeInfo(
            episodeId = subjectId * 10,
            name = "Ep",
            nameCn = "",
            airDate = PackedDate(2026, 9, 4),
            timezone = UTC9,
            sort = EpisodeSort(1),
            ep = EpisodeSort(1),
        ),
        airingTime = airingTime,
        timeKnown = timeKnown,
    )

    private fun scheduleFor(today: LocalDate): List<AiringScheduleForDate> =
        SchedulePageDataHelper.OFFSET_DAYS_RANGE.map { offset ->
            val date = today.plus(DatePeriod(days = offset))
            AiringScheduleForDate(
                date = date,
                list = listOf(
                    episode(1, date.atStartOfDayIn(timeZone), timeKnown = false),
                    episode(2, date.atStartOfDayIn(timeZone) + 20.hours, timeKnown = true),
                ),
            )
        }

    private val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewModels = mutableListOf<ScheduleViewModel>()

    private fun newViewModel(useCase: GetAnimeScheduleFlowUseCase, clock: Clock): ScheduleViewModel =
        ScheduleViewModel(koin = koinWith(useCase), timeZone = timeZone, clock = clock).also { viewModels += it }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.backgroundScope.cancel() }
        viewModels.clear()
        fixtureScope.cancel()
    }

    private suspend fun <T> awaitReal(block: suspend () -> T): T = withContext(Dispatchers.Default) {
        withTimeout(20.seconds) { block() }
    }

    private fun List<ScheduleDay>.today(): LocalDate = first { it.kind == ScheduleDay.Kind.TODAY }.date

    private fun assertColumnsMatchDays(presentation: SchedulePagePresentation) {
        assertEquals(
            presentation.days.map { it.date },
            presentation.airingSchedules.map { it.date },
            "columns must cover exactly the days of the header (placeholder=${presentation.isPlaceholder})",
        )
    }

    @Test
    fun `initial state uses today from the injected clock and time zone`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)

        assertEquals(today, vm.pageState.days.today())
        assertEquals(15, vm.pageState.days.size)
        val initial = vm.presentationFlow.value
        assertTrue(initial.isPlaceholder)
        assertNull(initial.error)
        assertEquals(today, initial.days.today())
        assertEquals(today.plus(DatePeriod(days = -7)), initial.airingSchedules.first().date)
        assertEquals(today.plus(DatePeriod(days = 7)), initial.airingSchedules.last().date)
        assertColumnsMatchDays(initial)
        assertEquals(initial.days, vm.pageState.days)

        assertEquals(emptyList(), useCase.calls.value)
    }

    @Test
    fun `presentation renders unknown-time items after timed items`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val presentation = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertNull(presentation.error)
        assertEquals(listOf(today), useCase.calls.value)
        assertEquals(today, presentation.days.today())
        assertColumnsMatchDays(presentation)

        val todayColumn = presentation.airingSchedules.first { it.date == today }.episodes

        assertEquals(3, todayColumn.size)
        assertIs<AiringScheduleColumnItem.CurrentTimeIndicator>(todayColumn[0])
        val timed = assertIs<AiringScheduleColumnItem.Data>(todayColumn[1])
        assertEquals(2, timed.item.subjectId)
        assertNotNull(timed.item.time)
        assertTrue(timed.showTime)
        val unknown = assertIs<AiringScheduleColumnItem.Data>(todayColumn[2])
        assertEquals(1, unknown.item.subjectId)
        assertNull(unknown.item.time)
        assertTrue(unknown.showTime)

        val otherColumn = presentation.airingSchedules.first { it.date != today }.episodes
        assertEquals(2, otherColumn.size)
        assertTrue(otherColumn.none { it is AiringScheduleColumnItem.CurrentTimeIndicator })

        subscription.cancel()
    }

    @Test
    fun `crossing local midnight moves header and columns together and shows loading before the new response`() =
        runTest {
            val day1 = LocalDate(2026, 9, 4)
            val day2 = LocalDate(2026, 9, 5)

            val day2Response = CompletableDeferred<Unit>()
            val useCase = FakeGetAnimeScheduleFlowUseCase { day, callIndex ->
                flow {
                    if (callIndex >= 1) day2Response.await()
                    emit(scheduleFor(day))
                }
            }

            val clock = OffsetClock(LocalDateTime(2026, 9, 5, 0, 0).toInstant(timeZone) - 500.milliseconds)
            val vm = newViewModel(useCase, clock)
            assertEquals(day1, vm.pageState.days.today())
            assertEquals(day1, vm.presentationFlow.value.days.today())

            val observed = MutableStateFlow<List<SchedulePagePresentation>>(emptyList())
            val subscription = fixtureScope.launch {
                vm.presentationFlow.collect { presentation -> observed.update { it + presentation } }
            }

            val loadedDay1 = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
            assertEquals(day1, loadedDay1.days.today())
            assertColumnsMatchDays(loadedDay1)
            assertEquals(listOf(day1), useCase.calls.value)

            val loadingDay2 = awaitReal { vm.presentationFlow.first { it.days.today() == day2 } }
            assertTrue(loadingDay2.isPlaceholder, "the first presentation for the new day must be the loading placeholder")
            assertNull(loadingDay2.error)
            assertEquals(day2.plus(DatePeriod(days = -7)), loadingDay2.days.first().date)
            assertEquals(day2.plus(DatePeriod(days = 7)), loadingDay2.days.last().date)
            assertColumnsMatchDays(loadingDay2)
            assertEquals(loadingDay2.days, vm.pageState.days)

            awaitReal { useCase.calls.first { it.size >= 2 } }
            assertEquals(listOf(day1, day2), useCase.calls.value)

            assertTrue(vm.presentationFlow.value.isPlaceholder)
            assertEquals(day2, vm.presentationFlow.value.days.today())

            awaitReal { observed.first { list -> list.any { it.isPlaceholder && it.days.today() == day2 } } }

            day2Response.complete(Unit)
            val presentation = awaitReal {
                vm.presentationFlow.first { !it.isPlaceholder && it.days.today() == day2 }
            }
            assertNull(presentation.error)
            assertEquals(day2.plus(DatePeriod(days = -7)), presentation.days.first().date)
            assertEquals(day2.plus(DatePeriod(days = 7)), presentation.days.last().date)
            assertEquals(day2.plus(DatePeriod(days = -7)), presentation.airingSchedules.first().date)
            assertEquals(day2.plus(DatePeriod(days = 7)), presentation.airingSchedules.last().date)
            assertColumnsMatchDays(presentation)

            assertEquals(
                listOf(day2),
                presentation.airingSchedules.filter { schedule ->
                    schedule.episodes.any { it is AiringScheduleColumnItem.CurrentTimeIndicator }
                }.map { it.date },
            )

            assertEquals(day2, vm.pageState.days.today())
            assertEquals(presentation.days, vm.pageState.days)

            val all = awaitReal { observed.first { list -> list.any { !it.isPlaceholder && it.days.today() == day2 } } }
            all.forEach(::assertColumnsMatchDays)
            val firstDay2Loading = all.indexOfFirst { it.isPlaceholder && it.days.today() == day2 }
            val firstDay2Loaded = all.indexOfFirst { !it.isPlaceholder && it.days.today() == day2 }
            assertTrue(firstDay2Loading >= 0, "a loading presentation for the new day must be observed")
            assertTrue(
                firstDay2Loading < firstDay2Loaded,
                "loading (index $firstDay2Loading) must precede loaded (index $firstDay2Loaded)",
            )

            assertTrue(all.drop(firstDay2Loading).none { it.days.today() == day1 })

            subscription.cancel()
        }

    @Test
    fun `refresh re-invokes the use case with the current today`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(listOf(today), useCase.calls.value)

        vm.refresh()
        awaitReal { useCase.calls.first { it.size >= 2 } }
        assertEquals(listOf(today, today), useCase.calls.value)

        vm.refresh()
        awaitReal { useCase.calls.first { it.size >= 3 } }
        assertEquals(listOf(today, today, today), useCase.calls.value)

        subscription.cancel()
    }

    @Test
    fun `refresh shows a loading presentation until the new response arrives`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val secondResponse = CompletableDeferred<Unit>()
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, callIndex ->
            flow {
                if (callIndex >= 1) secondResponse.await()
                emit(scheduleFor(day))
            }
        }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val loadedBefore = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(listOf(today), useCase.calls.value)

        vm.refresh()
        val loading = awaitReal { vm.presentationFlow.first { it.isPlaceholder } }
        assertNull(loading.error)
        assertEquals(loadedBefore.days, loading.days)
        assertColumnsMatchDays(loading)
        assertEquals(loading.days, vm.pageState.days)
        awaitReal { useCase.calls.first { it.size >= 2 } }
        assertEquals(listOf(today, today), useCase.calls.value)
        assertTrue(vm.presentationFlow.value.isPlaceholder)

        secondResponse.complete(Unit)
        val loaded = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertNull(loaded.error)
        assertEquals(15, loaded.airingSchedules.size)
        assertColumnsMatchDays(loaded)

        subscription.cancel()
    }

    @Test
    fun `refresh recovers from an error`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, callIndex ->
            flow {
                if (callIndex == 0) throw IllegalStateException("network down")
                emit(scheduleFor(day))
            }
        }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val failed = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertNotNull(failed.error)
        assertEquals(emptyList(), failed.airingSchedules)
        assertEquals(today, failed.days.today())

        vm.refresh()
        val loaded = awaitReal { vm.presentationFlow.first { !it.isPlaceholder && it.error == null } }
        assertEquals(listOf(today, today), useCase.calls.value)
        assertEquals(15, loaded.airingSchedules.size)

        subscription.cancel()
    }

}
