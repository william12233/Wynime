package com.wynime.app.domain.session.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OAuthCallbackRegistry {
    data class Callback(
        val state: String,
        val ticket: String?,
        val error: String?,
    )

    private val mutex = Mutex()
    private val callbacks = mutableMapOf<String, Callback>()

    suspend fun publish(state: String, ticket: String?, error: String?) {
        require(state.isNotBlank()) { "OAuth callback state must not be blank" }
        require(!ticket.isNullOrBlank() || !error.isNullOrBlank()) {
            "OAuth callback must contain a ticket or an error"
        }
        mutex.withLock {
            callbacks[state] = Callback(
                state = state,
                ticket = ticket?.takeIf(String::isNotBlank),
                error = error?.takeIf(String::isNotBlank),
            )
        }
    }

    suspend fun take(state: String): Callback? = mutex.withLock {
        callbacks.remove(state)
    }
}
