package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackEvent

class SwitchNextEpisodeExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val getNextEpisode: suspend (currentEpisodeId: Int) -> Int?,
) : PlayerExtension("SwitchNextEpisode") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val mediaLoaded = CompletableDeferred<Unit>()
        backgroundTaskScope.launch("MediaLoadedListener") {
            context.subscribeEvents<EpisodeFetchSelectPlayState.MediaLoadedEvent>().collectLatest {
                if (mediaLoaded.isActive) mediaLoaded.complete(Unit)
            }
        }

        backgroundTaskScope.launch("SwitchNextEpisode") {
            mediaLoaded.await()
            context.sessionFlow.collectLatest { session ->
                getVideoScaffoldConfigUseCase()
                    .map { it.autoPlayNext }
                    .distinctUntilChanged()
                    .collectLatest inner@{ enabled ->
                        if (!enabled) return@inner

                        impl(session)
                    }
            }
        }
    }

    private suspend fun impl(session: EpisodeSession): Nothing {
        val player = context.player
        player.events.collect { event ->
            if (event !is PlaybackEvent.MediaEnded) return@collect
            val durationMillis = event.durationMillis
            val closeToEnd = durationMillis != null && durationMillis - event.finalPositionMillis < 5000

            if (closeToEnd) {
                val nextEpisode = getNextEpisode(session.episodeId)
                logger.info("播放完毕，切换下一集 $nextEpisode")
                context.switchEpisode(nextEpisode ?: return@collect)
            }
        }
    }

    class Factory(
        private val getNextEpisode: suspend (currentEpisodeId: Int) -> Int?,
    ) : EpisodePlayerExtensionFactory<SwitchNextEpisodeExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): SwitchNextEpisodeExtension {
            return SwitchNextEpisodeExtension(context, koin, getNextEpisode)
        }
    }

    companion object {
        private val logger = logger<SwitchNextEpisodeExtension>()
    }
}
