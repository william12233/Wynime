package com.wynime.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.wynime.app.data.models.schedule.AnimeSeasonId
import com.wynime.app.data.repository.episode.AnimeScheduleRepository
import com.wynime.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext

fun interface GetAnimeSeasonIdsFlowUseCase : UseCase {
    operator fun invoke(): Flow<List<AnimeSeasonId>>

    companion object {

        fun sorted(seasons: List<AnimeSeasonId>): List<AnimeSeasonId> = seasons.sortedDescending()
    }
}

class GetAnimeSeasonIdsFlowUseCaseImpl(
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : GetAnimeSeasonIdsFlowUseCase {
    override fun invoke(): Flow<List<AnimeSeasonId>> =
        flow { emit(GetAnimeSeasonIdsFlowUseCase.sorted(animeScheduleRepository.getSeasonIds())) }
            .flowOn(defaultDispatcher)
}
