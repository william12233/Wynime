package com.wynime.app.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

fun interface EpisodeNavigationGuard {
    fun checkNavigateEpisode(subjectId: Int, episodeId: Int): String?
}

fun interface EpisodeNavigationGuardHandle {
    fun dispose()
}

object EpisodeNavigationGuardRegistry {
    private val guards = MutableStateFlow<List<EpisodeNavigationGuard>>(emptyList())
    private val _denialEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val denialEvents: SharedFlow<String> = _denialEvents.asSharedFlow()

    fun register(guard: EpisodeNavigationGuard): EpisodeNavigationGuardHandle {
        guards.update { it + guard }
        return EpisodeNavigationGuardHandle { guards.update { current -> current - guard } }
    }

    internal fun check(subjectId: Int, episodeId: Int): String? =
        guards.value.firstNotNullOfOrNull { it.checkNavigateEpisode(subjectId, episodeId) }

    internal fun emitDenial(reason: String) {
        _denialEvents.tryEmit(reason)
    }

    fun checkOrNotifyDenied(subjectId: Int, episodeId: Int): Boolean {
        val reason = check(subjectId, episodeId) ?: return true
        emitDenial(reason)
        return false
    }
}
