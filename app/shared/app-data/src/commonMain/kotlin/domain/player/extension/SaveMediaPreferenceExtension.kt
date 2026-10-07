package com.wynime.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import org.koin.core.Koin

class SaveMediaPreferenceExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("SaveMediaPreference") {
    private val mediaSelectorEventSavePreferenceUseCase: MediaSelectorEventSavePreferenceUseCase by koin.inject()
    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("SaveMediaPreference") {
            context.sessionFlow.flatMapLatest { it.fetchSelectFlow }.collectLatest { bundle ->
                if (bundle == null) return@collectLatest
                mediaSelectorEventSavePreferenceUseCase(bundle.mediaSelector, context.subjectId)
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<SaveMediaPreferenceExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): SaveMediaPreferenceExtension {
            return SaveMediaPreferenceExtension(context, koin)
        }
    }
}