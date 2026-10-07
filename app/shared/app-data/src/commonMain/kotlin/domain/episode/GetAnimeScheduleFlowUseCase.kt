package com.wynime.app.domain.episode

import androidx.compose.ui.util.packInts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.wynime.app.data.models.subject.LightEpisodeInfo
import com.wynime.app.data.models.subject.LightSubjectInfo
import com.wynime.app.data.repository.episode.AnimeScheduleRepository
import com.wynime.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

data class AiringScheduleForDate(
    val date: LocalDate,
    val list: List<EpisodeWithAiringTime>,
)

data class EpisodeWithAiringTime(
    val subject: LightSubjectInfo,
    val episode: LightEpisodeInfo,
    val airingTime: Instant,
    val timeKnown: Boolean,
) {
    val combinedId = packInts(subject.subjectId, episode.episodeId)
}

fun interface GetAnimeScheduleFlowUseCase : UseCase {
    operator fun invoke(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>>

    companion object {
        val OFFSET_DAYS_RANGE = (-7..7)
    }
}

class GetAnimeScheduleFlowUseCaseImpl(
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : GetAnimeScheduleFlowUseCase {
    override fun invoke(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>> =
        animeScheduleRepository.recentAiringSchedulesFlow(today, timeZone)
            .flowOn(defaultDispatcher)
}
