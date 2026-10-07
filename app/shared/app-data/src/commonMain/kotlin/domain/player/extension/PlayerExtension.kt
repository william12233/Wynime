package com.wynime.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.player.VideoLoadingState
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer

abstract class PlayerExtension(
    val name: String,
) {

    open fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
    }

    open suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {}

    open suspend fun onClose() {}
}

interface ExtensionBackgroundTaskScope {

    fun launch(subName: String, block: suspend CoroutineScope.() -> Unit): Job
}

interface PlayerExtensionContext {
    val subjectId: Int

    val player: MediampPlayer
    val videoLoadingStateFlow: Flow<VideoLoadingState>

    val sessionFlow: Flow<EpisodeSession>

    val broadcastEvent: SharedFlow<PlayerExtensionEvent>

    @UnsafeEpisodeSessionApi
    suspend fun getCurrentEpisodeId(): Int
    suspend fun switchEpisode(newEpisodeId: Int)

    suspend fun broadcast(event: PlayerExtensionEvent)
}

inline fun <reified T : PlayerExtensionEvent> PlayerExtensionContext.subscribeEvents(): Flow<T> {
    return broadcastEvent.filterIsInstance<T>()
}

interface PlayerExtensionEvent

fun interface EpisodePlayerExtensionFactory<T : PlayerExtension> {
    fun create(context: PlayerExtensionContext, koin: Koin): T
}
