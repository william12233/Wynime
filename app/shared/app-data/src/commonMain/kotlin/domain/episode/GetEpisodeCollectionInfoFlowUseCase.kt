package com.wynime.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

fun interface GetEpisodeCollectionInfoFlowUseCase : UseCase {
    operator fun invoke(subjectId: Int, episodeId: Int): Flow<EpisodeCollectionInfo>
}

class GetEpisodeCollectionInfoFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetEpisodeCollectionInfoFlowUseCase, KoinComponent {
    private val repository: EpisodeCollectionRepository by inject()
    override fun invoke(subjectId: Int, episodeId: Int): Flow<EpisodeCollectionInfo> {
        return repository.episodeCollectionInfoFlow(subjectId, episodeId).flowOn(flowContext)
    }
}
