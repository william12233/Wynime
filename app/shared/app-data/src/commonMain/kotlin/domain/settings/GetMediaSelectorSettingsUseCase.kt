package com.wynime.app.domain.settings

import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.usecase.UseCase

interface GetMediaSelectorSettingsUseCase : UseCase {
    operator fun invoke(): Flow<MediaSelectorSettings>
}

class GetMediaSelectorSettingsUseCaseImpl(
    private val settingsRepository: SettingsRepository,
) : GetMediaSelectorSettingsUseCase {
    override fun invoke(): Flow<MediaSelectorSettings> = settingsRepository.mediaSelectorSettings.flow
}
