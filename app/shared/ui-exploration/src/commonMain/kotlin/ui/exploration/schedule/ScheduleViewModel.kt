package com.wynime.app.ui.exploration.schedule

import androidx.compose.runtime.mutableStateOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.wynime.app.domain.episode.AiringScheduleForDate
import com.wynime.app.domain.episode.GetAnimeScheduleFlowUseCase
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.catching
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.Koin

open class ScheduleViewModel(
    koin: Koin = GlobalKoin,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : AbstractViewModel() {
    private val getAnimeScheduleFlowUseCase: GetAnimeScheduleFlowUseCase by koin.inject()

    private fun currentToday(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    private val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(now.toLocalDateTime(timeZone).date)

            delay(delayUntilNextMidnight(now, timeZone).coerceAtLeast(1.seconds))
        }
    }.distinctUntilChanged()

    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(delayUntilNextMinute(clock.now()))
        }
    }

    private class ScheduleLoad(
        val today: LocalDate,
        val result: Result<List<AiringScheduleForDate>>?,
    )

    private val airingSchedulesFlowRestarter = FlowRestarter()
    private val airingSchedulesFlow: Flow<ScheduleLoad> = todayFlow
        .flatMapLatest { today ->
            getAnimeScheduleFlowUseCase(today, timeZone = timeZone)
                .catching()
                .map { ScheduleLoad(today, it) }

                .onStart { emit(ScheduleLoad(today, result = null)) }
        }
        .restartable(airingSchedulesFlowRestarter)
        .shareInBackground(started = SharingStarted.Lazily)

    fun refresh() {
        airingSchedulesFlowRestarter.restart()
    }

    private val presentationState = mutableStateOf(ScheduleLoad(currentToday(), result = null).toPresentation(clock.now()))

    val presentationFlow: StateFlow<SchedulePagePresentation> = combine(airingSchedulesFlow, minuteTicker) { load, _ ->
        load.toPresentation(clock.now())
    }
        .onEach { presentationState.value = it }
        .stateIn(
            backgroundScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = presentationState.value,
        )

    val pageState = ScheduleScreenState { presentationState.value.days }

    private fun ScheduleLoad.toPresentation(now: Instant): SchedulePagePresentation {
        val days = ScheduleDay.generateForRecentTwoWeeks(today)
        val loaded = result
            ?: return SchedulePagePresentation(
                days = days,
                airingSchedules = generatePlaceholderAiringScheduleList(today),
                error = null,
                isPlaceholder = true,
            )

        val timeZone = timeZone
        val currentDateTime = now.toLocalDateTime(timeZone)
        return SchedulePagePresentation(
            days = days,
            airingSchedules = loaded.getOrNull()?.map { airingSchedule ->
                AiringSchedule(
                    airingSchedule.date,
                    SchedulePageDataHelper.toColumnItems(
                        airingSchedule.list.map { it.toPresentation(timeZone) },
                        addIndicator = currentDateTime.date == airingSchedule.date,
                        currentDateTime.time,
                    ),
                )
            }.orEmpty(),
            error = loaded.exceptionOrNull()?.let { LoadError.fromException(it) },
        )
    }

    companion object {

        internal fun delayUntilNextMidnight(now: Instant, timeZone: TimeZone): Duration {
            val today = now.toLocalDateTime(timeZone).date
            val nextMidnight = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone)
            return nextMidnight - now
        }

        internal fun delayUntilNextMinute(now: Instant): Duration {
            val millisInMinute = 60_000L
            return (millisInMinute - now.toEpochMilliseconds().mod(millisInMinute)).milliseconds
        }
    }
}

data class SchedulePagePresentation(
    val days: List<ScheduleDay>,
    val airingSchedules: List<AiringSchedule>,
    val error: LoadError?,
    val isPlaceholder: Boolean = false,
)

private fun generatePlaceholderAiringScheduleList(
    baseDate: LocalDate,
): List<AiringSchedule> {
    val episodes = (1..10).map {
        AiringScheduleColumnItem.PlaceholderData(id = it, showTime = true)
    }
    return SchedulePageDataHelper.OFFSET_DAYS_RANGE.map { offset ->
        AiringSchedule(
            baseDate.plus(DatePeriod(days = offset)),
            episodes = episodes,
        )
    }
}

@TestOnly
fun createTestSchedulePagePresentation() = SchedulePagePresentation(
    days = ScheduleDay.generateForRecentTwoWeeks(LocalDate(2025, 12, 10)),
    airingSchedules = TestSchedulePageData,
    null,
)
