package com.wynime.app.data.repository.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.wynime.app.data.models.schedule.AnimeSeasonId
import com.wynime.app.data.network.BangumiCalendarRepository
import com.wynime.app.data.network.BangumiScheduleService
import com.wynime.app.data.repository.Repository
import com.wynime.app.domain.episode.AiringScheduleForDate
import com.wynime.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class AnimeScheduleRepository(
    private val calendarRepository: BangumiCalendarRepository,
    private val bangumiScheduleService: BangumiScheduleService,
    private val updatePeriod: Duration = 1.hours,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    private val refreshTicker = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(updatePeriod)
        }
    }

    suspend fun getSeasonIds(): List<AnimeSeasonId> = calendarRepository.getCalendarDays()
        .flatMap { it.items }
        .mapNotNull { it.airDate }
        .map { AnimeSeasonId.fromDate(it.year, it.monthNumber) }
        .distinct()

    fun recentAiringSchedulesFlow(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>> {
        var forceBangumiRefresh = true
        return refreshTicker.mapLatest {
            try {
                bangumiScheduleService.getRecentAiringSchedules(
                        today = today,
                        timeZone = timeZone,
                        forceRefresh = forceBangumiRefresh,
                ).also { forceBangumiRefresh = false }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) {
                    "Failed to load airing schedule (operation=Bangumi calendar plus episodes)."
                }
                throw e
            }
        }.flowOn(defaultDispatcher)
    }
}

