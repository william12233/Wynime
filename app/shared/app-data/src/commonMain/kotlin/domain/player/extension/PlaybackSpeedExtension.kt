package com.wynime.app.domain.player.extension

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.wynime.app.domain.episode.EpisodeSession
import org.koin.core.Koin
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.isMediaLoaded

class PlaybackSpeedExtension(
    private val context: PlayerExtensionContext,
    private val playbackSpeedFlow: Flow<Float>,
) : PlayerExtension("PlaybackSpeed") {
    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("PlaybackSpeed") {
            combine(
                playbackSpeedFlow,
                context.player.state.map { it.isMediaLoaded }.distinctUntilChanged(),
            ) { speed, _ ->
                speed
            }.collect { speed ->
                withContext(context.player.mainDispatcher) {
                    context.player.features[PlaybackSpeed]?.set(speed)
                }
            }
        }
    }

    class Factory(
        private val playbackSpeedFlow: Flow<Float>,
    ) : EpisodePlayerExtensionFactory<PlaybackSpeedExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): PlaybackSpeedExtension {
            return PlaybackSpeedExtension(context, playbackSpeedFlow)
        }
    }
}
