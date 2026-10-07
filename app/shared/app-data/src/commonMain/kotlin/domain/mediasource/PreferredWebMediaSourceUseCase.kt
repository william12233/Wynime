package com.wynime.app.domain.mediasource

import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.domain.usecase.UseCase

fun interface GetPreferredWebMediaSourceUseCase : UseCase {
    operator fun invoke(subjectId: Int): Flow<String?>
}

fun interface SetPreferredWebMediaSourceUseCase : UseCase {

    suspend operator fun invoke(subjectId: Int, mediaSourceId: String?)
}

class GetPreferredWebMediaSourceUseCaseImpl(
    private val repository: EpisodePreferencesRepository,
) : GetPreferredWebMediaSourceUseCase {
    override operator fun invoke(subjectId: Int): Flow<String?> {
        return repository.getPreferredWebMediaSource(subjectId)
    }
}

class SetPreferredWebMediaSourceUseCaseImpl(
    private val repository: EpisodePreferencesRepository,
) : SetPreferredWebMediaSourceUseCase {
    override suspend fun invoke(subjectId: Int, mediaSourceId: String?) {
        if (mediaSourceId != null) {
            repository.setPreferredWebMediaSource(subjectId, mediaSourceId)
        } else {
            repository.removePreferredWebMediaSource(subjectId)
        }
    }
}