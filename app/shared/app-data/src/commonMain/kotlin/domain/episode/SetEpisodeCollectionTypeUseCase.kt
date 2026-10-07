package com.wynime.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.usecase.UseCase
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.coroutines.retryWithBackoffDelay
import org.koin.core.Koin

data class SetEpisodeCollectionTypeRequest(
    val subjectId: Int,
    val episodeId: Int,
    val collectionType: UnifiedCollectionType
)

fun interface SetEpisodeCollectionTypeUseCase : UseCase {
    suspend operator fun invoke(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType,
    )

    suspend fun invokeSafe(request: SetEpisodeCollectionTypeRequest): LoadError? {
        return LoadError.runAndWrapOrThrowCancellation {
            invoke(request.subjectId, request.episodeId, request.collectionType)
        }
    }
}

class SetEpisodeCollectionTypeUseCaseImpl(
    koin: Koin,
) : SetEpisodeCollectionTypeUseCase {
    private val episodeCollectionRepository: EpisodeCollectionRepository by koin.inject()
    override suspend fun invoke(subjectId: Int, episodeId: Int, collectionType: UnifiedCollectionType) {
        withContext(Dispatchers.Default) {
            suspend {

                episodeCollectionRepository.setEpisodeCollectionType(subjectId, episodeId, collectionType)
            }.asFlow().retryWithBackoffDelay(3).first()
        }
    }
}

