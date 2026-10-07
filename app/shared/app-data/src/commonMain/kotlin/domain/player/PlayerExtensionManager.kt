package com.wynime.app.domain.player

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.getCurrentEpisodeId
import com.wynime.app.domain.episode.player
import com.wynime.app.domain.player.extension.EpisodePlayerExtensionFactory
import com.wynime.app.domain.player.extension.PlayerExtension
import com.wynime.app.domain.player.extension.PlayerExtensionContext
import com.wynime.app.domain.player.extension.PlayerExtensionEvent
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.cancellation.CancellationException

class PlayerExtensionManager(
    factories: List<EpisodePlayerExtensionFactory<*>>,
    state: EpisodeFetchSelectPlayState,
    koin: Koin,
) {
    private val context = object : PlayerExtensionContext {
        override val subjectId: Int
            get() = state.subjectId

        override val player: MediampPlayer
            get() = state.player
        override val videoLoadingStateFlow: Flow<VideoLoadingState>
            get() = state.playerSession.videoLoadingState
        override val sessionFlow: Flow<EpisodeSession>
            get() = state.episodeSessionFlow

        override val broadcastEvent: MutableSharedFlow<PlayerExtensionEvent> =
            MutableSharedFlow(0, 1, BufferOverflow.DROP_OLDEST)

        @UnsafeEpisodeSessionApi
        override suspend fun getCurrentEpisodeId(): Int {
            return state.getCurrentEpisodeId()
        }

        @OptIn(UnsafeEpisodeSessionApi::class)
        override suspend fun switchEpisode(newEpisodeId: Int) {
            if (getCurrentEpisodeId() == newEpisodeId) {
                error("Cannot switch to the same episode: $newEpisodeId")
            }

            state.switchEpisode(newEpisodeId)
        }

        override suspend fun broadcast(event: PlayerExtensionEvent) {
            broadcastEvent.emit(event)
        }
    }

    val extensions: List<PlayerExtension> by lazy {
        factories.map { it.create(context, koin) }
    }

    inline fun call(block: (PlayerExtension) -> Unit) {
        extensions.forEach {
            try {
                block(it)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }

                throw ExtensionException("Error calling extension ${it.name}, see cause", e)
            }
        }
    }
}

class ExtensionException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
