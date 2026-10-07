package com.wynime.app.domain.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import com.wynime.app.data.repository.RepositoryAuthorizationException

interface SessionStateProvider {

    val stateFlow: Flow<SessionState>

    val eventFlow: Flow<SessionEvent>

    @Deprecated(
        "",
        ReplaceWith(
            "this.canAccessAniApiNow()",
            "com.wynime.app.domain.session.canAccessWynimeApiNow",
        ),
    )
    suspend fun isLoggedInNow() = canAccessBangumiApiNow()
}

sealed class SessionState {
    data class Invalid(
        val reason: InvalidSessionReason
    ) : SessionState()

    data class Valid(

        val bangumiConnected: Boolean,
    ) : SessionState()
}

sealed interface SessionEvent {

    data object NewLogin : SessionEvent

}

enum class InvalidSessionReason {

    NO_TOKEN,
    NETWORK_ERROR,
    UNKNOWN,
}

suspend fun SessionStateProvider.canAccessWynimeApiNow(): Boolean {
    return when (stateFlow.first()) {
        is SessionState.Invalid -> false
        is SessionState.Valid -> true
    }
}

suspend fun SessionStateProvider.canAccessBangumiApiNow(): Boolean {
    return when (val state = stateFlow.first()) {
        is SessionState.Invalid -> false
        is SessionState.Valid -> state.bangumiConnected
    }
}

suspend fun SessionStateProvider.checkAccessWynimeApiNow() {
    if (!canAccessWynimeApiNow()) {
        throw RepositoryAuthorizationException()
    }
}

suspend fun SessionStateProvider.checkAccessBangumiApiNow() {
    if (!canAccessBangumiApiNow()) {
        throw RepositoryAuthorizationException()
    }
}

fun <T> Flow<T>.restartOnNewLogin(sessionStateProvider: SessionStateProvider): Flow<T> =
    sessionStateProvider.stateFlow.flatMapLatest {
        this
    }
