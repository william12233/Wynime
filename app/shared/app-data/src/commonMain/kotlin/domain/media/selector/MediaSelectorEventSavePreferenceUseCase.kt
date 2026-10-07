package com.wynime.app.domain.media.selector

import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

fun interface MediaSelectorEventSavePreferenceUseCase : UseCase {
    suspend operator fun invoke(mediaSelector: MediaSelector, subjectId: Int)
}

object MediaSelectorEventSavePreferenceUseCaseImpl : MediaSelectorEventSavePreferenceUseCase, KoinComponent {
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()

    override suspend fun invoke(mediaSelector: MediaSelector, subjectId: Int) {
        mediaSelector.eventHandling.run {
            savePreferenceOnSelect {
                episodePreferencesRepository.setMediaPreference(subjectId, it)
            }
        }
    }
}