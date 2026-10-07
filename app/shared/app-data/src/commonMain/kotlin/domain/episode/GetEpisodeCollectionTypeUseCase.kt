package com.wynime.app.domain.episode

import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.domain.usecase.UseCase
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.koin.core.Koin

fun interface GetEpisodeCollectionTypeUseCase : UseCase {
    suspend operator fun invoke(
        subjectId: Int,
        episodeId: Int,
        allowNetwork: Boolean,
    ): UnifiedCollectionType?
}

class GetEpisodeCollectionTypeUseCaseImpl(
    koin: Koin,
) : GetEpisodeCollectionTypeUseCase {
    private val episodeCollectionRepository: EpisodeCollectionRepository by koin.inject()
    override suspend fun invoke(subjectId: Int, episodeId: Int, allowNetwork: Boolean): UnifiedCollectionType? {
        return episodeCollectionRepository.getEpisodeCollectionType(subjectId, episodeId, allowNetwork)
    }
}