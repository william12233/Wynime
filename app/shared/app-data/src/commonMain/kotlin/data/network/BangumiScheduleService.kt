package com.wynime.app.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import com.wynime.app.data.models.subject.LightEpisodeInfo
import com.wynime.app.data.models.subject.LightSubjectInfo
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.domain.episode.AiringScheduleForDate
import com.wynime.app.domain.episode.EpisodeWithAiringTime
import com.wynime.app.domain.episode.GetAnimeScheduleFlowUseCase
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BangumiScheduleService(
    private val dataSource: BangumiExploreDataSource,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val clock: Clock = Clock.System,
    private val cacheDuration: Duration = 30.minutes,
    private val calendarRepository: BangumiCalendarRepository =
        BangumiCalendarRepository(dataSource, ioDispatcher, clock, cacheDuration),
) {
    private val cacheMutex = Mutex()
    private val episodeSemaphore = Semaphore(MAX_EPISODE_REQUESTS)
    private val episodeCache = mutableMapOf<Int, CachedEpisodes>()

    suspend fun getRecentAiringSchedules(
        today: LocalDate,
        timeZone: TimeZone,
        forceRefresh: Boolean = false,
    ): List<AiringScheduleForDate> = withContext(ioDispatcher) {
        val dates = GetAnimeScheduleFlowUseCase.OFFSET_DAYS_RANGE.map { offset ->
            today.plus(DatePeriod(days = offset))
        }
        val calendar = getCalendarEntries(forceRefresh)
        val subjectIds = calendar
            .asSequence()
            .filter { it.type == ANIME_SUBJECT_TYPE }
            .map { it.id }
            .distinct()
            .toList()
        val episodesBySubject = coroutineScope {
            subjectIds.map { subjectId ->
                async {
                    subjectId to getEpisodes(subjectId, forceRefresh)
                }
            }.awaitAll().toMap()
        }
        assembleBangumiSchedule(
            dates = dates,
            timeZone = timeZone,
            calendar = calendar,
            episodesBySubject = episodesBySubject,
        )
    }

    private suspend fun getCalendarEntries(forceRefresh: Boolean): List<BangumiCalendarEntry> {
        val entries = try {
            calendarRepository.getCalendarDays(forceRefresh).flatMap { it.items }
        } catch (e: Throwable) {
            throw wrapRequestException("calendar", e)
        }
        cacheMutex.withLock {
            if (forceRefresh) episodeCache.clear()
        }
        return entries
    }

    private suspend fun getEpisodes(subjectId: Int, forceRefresh: Boolean): List<BangumiExploreEpisode> {
        val now = clock.now()
        if (!forceRefresh) {
            cacheMutex.withLock {
                episodeCache[subjectId]
                    ?.takeIf { now - it.cachedAt < cacheDuration }
                    ?.episodes
                    ?.let { return it }
            }
        }

        val episodes = episodeSemaphore.withPermit {
            try {
                dataSource.getEpisodes(subjectId)
            } catch (e: Throwable) {
                throw wrapRequestException("episodes for subject $subjectId", e)
            }
        }
        cacheMutex.withLock {
            episodeCache[subjectId] = CachedEpisodes(episodes = episodes, cachedAt = clock.now())
        }
        return episodes
    }

    private fun wrapRequestException(operation: String, throwable: Throwable): RepositoryException {
        if (throwable is BangumiExploreRequestException) {
            return RepositoryRequestError(
                localizedMessage = "Bangumi $operation request failed.",
                message = throwable.message,
                cause = throwable,
            )
        }
        return RepositoryException.wrapOrThrowCancellation(throwable)
    }

    private data class CachedEpisodes(
        val episodes: List<BangumiExploreEpisode>,
        val cachedAt: Instant,
    )

    private companion object {
        const val ANIME_SUBJECT_TYPE = 2
        const val MAX_EPISODE_REQUESTS = 6
    }
}

internal fun assembleBangumiSchedule(
    dates: List<LocalDate>,
    timeZone: TimeZone,
    calendar: List<BangumiCalendarEntry>,
    episodesBySubject: Map<Int, List<BangumiExploreEpisode>>,
): List<AiringScheduleForDate> {
    val subjectsById = calendar.associateBy { it.id }
    val dateSet = dates.toSet()
    val episodesByDate = dates.associateWith { mutableListOf<EpisodeWithAiringTime>() }

    episodesBySubject.forEach { (subjectId, episodes) ->
        val subject = subjectsById[subjectId] ?: return@forEach
        val subjectNameCn = subject.nameCn.ifBlank { subject.name }.ifBlank { "Bangumi #$subjectId" }
        val subjectName = subject.name.ifBlank { subjectNameCn }
        episodes.asSequence()
            .filter { it.id > 0 && it.type in MAIN_EPISODE_TYPES }
            .mapNotNull { episode ->
                val airDate = episode.airDate?.takeIf { it in dateSet } ?: return@mapNotNull null
                val episodeType = episodeTypeOf(episode.type)
                val episodeSort = EpisodeSort(BigNum(episode.sort.ifBlank { "0" }), episodeType)
                airDate to EpisodeWithAiringTime(
                    subject = LightSubjectInfo(
                        subjectId = subjectId,
                        name = subjectName,
                        nameCn = subjectNameCn,
                        imageLarge = subject.imageLarge,
                    ),
                    episode = LightEpisodeInfo(
                        episodeId = episode.id,
                        name = episode.name,
                        nameCn = episode.nameCn,
                        airDate = PackedDate.parseFromDate(airDate.toString()),
                        timezone = timeZone,
                        sort = episodeSort,
                        ep = episode.ep?.takeIf { it.isNotBlank() }?.let {
                            EpisodeSort(BigNum(it), episodeType)
                        },
                    ),
                    airingTime = airDate.atStartOfDayIn(timeZone),
                    timeKnown = false,
                )
            }
            .forEach { (date, episode) ->
                episodesByDate[date]?.add(episode)
            }
    }

    return dates.map { date ->
        AiringScheduleForDate(
            date = date,
            list = episodesByDate[date].orEmpty().sortedWith(
                compareBy<EpisodeWithAiringTime> { it.episode.sort }
                    .thenBy { it.subject.subjectId }
                    .thenBy { it.episode.episodeId },
            ),
        )
    }
}

private fun episodeTypeOf(type: Int): EpisodeType? = when (type) {
    0 -> EpisodeType.MainStory
    1 -> EpisodeType.SP
    else -> null
}

private val MAIN_EPISODE_TYPES = setOf(0, 1)
