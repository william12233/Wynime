package com.wynime.app.domain.settings

import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

fun interface GetMediaSelectorSettingsFlowUseCase : UseCase {
    operator fun invoke(): Flow<MediaSelectorSettings>
}

object GetMediaSelectorSettingsFlowUseCaseImpl : GetMediaSelectorSettingsFlowUseCase, KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    override fun invoke(): Flow<MediaSelectorSettings> = settingsRepository.mediaSelectorSettings.flow
}

