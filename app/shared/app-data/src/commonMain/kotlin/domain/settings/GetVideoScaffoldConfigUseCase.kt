package com.wynime.app.domain.settings

import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

fun interface GetVideoScaffoldConfigUseCase : UseCase {
    operator fun invoke(): Flow<VideoScaffoldConfig>
}

object GetVideoScaffoldConfigUseCaseImpl : GetVideoScaffoldConfigUseCase, KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    override fun invoke(): Flow<VideoScaffoldConfig> = settingsRepository.videoScaffoldConfig.flow
}
