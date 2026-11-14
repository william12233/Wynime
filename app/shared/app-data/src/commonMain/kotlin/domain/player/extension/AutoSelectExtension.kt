package com.wynime.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import org.koin.core.Koin

class AutoSelectExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("AutoSelect") {
    private val mediaSelectorAutoSelectUseCase: MediaSelectorAutoSelectUseCase by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("AutoSelect") {
            combine(
                context.sessionFlow.flatMapLatest { it.fetchSelectFlow },
                context.mediaAutoSelectEnabled,
            ) { fetchSelect, enabled -> fetchSelect to enabled }.collectLatest { (fetchSelect, enabled) ->
                if (fetchSelect == null || !enabled) return@collectLatest
                mediaSelectorAutoSelectUseCase(fetchSelect.mediaFetchSession, fetchSelect.mediaSelector)
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<AutoSelectExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): AutoSelectExtension {
            return AutoSelectExtension(context, koin)
        }
    }
}
