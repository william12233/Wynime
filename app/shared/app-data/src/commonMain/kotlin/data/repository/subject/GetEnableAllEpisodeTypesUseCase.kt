package com.wynime.app.data.repository.subject

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.usecase.UseCase
import com.wynime.datasources.api.EpisodeType

fun interface GetEpisodeTypeFiltersUseCase : UseCase {
    operator fun invoke(): Flow<List<EpisodeType>>
}

class GetEpisodeTypeFiltersUseCaseImpl(
    private val settingsRepository: SettingsRepository,
) : GetEpisodeTypeFiltersUseCase {
    private val allowed = listOf(EpisodeType.MainStory, EpisodeType.SP, EpisodeType.OVA, EpisodeType.OAD)

    override fun invoke(): Flow<List<EpisodeType>> = settingsRepository.debugSettings.flow.map {
        if (it.showAllEpisodes) {
            EpisodeType.entries
        } else {
            allowed
        }
    }

}