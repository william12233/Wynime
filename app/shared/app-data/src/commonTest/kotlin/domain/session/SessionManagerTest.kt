package com.wynime.app.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.GuestSession
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SessionManagerTest {
    private val nowMillis = 1_000_000L

    @Test
    fun `startup clears expired access token session`() = runTest {
        val store = MemoryDataStore(
            TokenSave(
                refreshToken = "refresh",
                accessTokens = TokenSave.AccessTokens(
                    bangumiAccessToken = "bangumi",
                    legacyServiceAccessToken = "ani",
                    expiresAtMillis = nowMillis,
                ),
            ),
        )
        val repository = TokenRepository(store)
        val manager = createSessionManager(repository, backgroundScope)

        manager.clearSessionIfAccessTokenExpired()

        assertEquals(TokenSave.Initial, store.data.value)
        assertEquals(GuestSession, repository.session.value())
    }

    @Test
    fun `startup keeps valid access token session`() = runTest {
        val save = TokenSave(
            refreshToken = "refresh",
            accessTokens = TokenSave.AccessTokens(
                bangumiAccessToken = "bangumi",
                legacyServiceAccessToken = "ani",
                expiresAtMillis = nowMillis + 2.hours.inWholeMilliseconds,
            ),
        )
        val store = MemoryDataStore(save)
        val repository = TokenRepository(store)
        val manager = createSessionManager(repository, backgroundScope)

        manager.clearSessionIfAccessTokenExpired()

        assertEquals(save, store.data.value)
        assertEquals(
            AccessTokenSession(
                AccessTokenPair(
                    legacyServiceAccessToken = "ani",
                    expiresAtMillis = nowMillis + 2.hours.inWholeMilliseconds,
                    bangumiAccessToken = "bangumi",
                ),
            ),
            repository.session.value(),
        )
    }

    private fun createSessionManager(repository: TokenRepository, coroutineScope: CoroutineScope): SessionManager {
        return SessionManager(
            tokenRepository = repository,
            coroutineScope = coroutineScope,
            refreshSession = SessionManager.SessionRefresher {
                error("refresh should not be called")
            },
            clock = FixedClock(nowMillis),
        )
    }

    private suspend fun <T> Flow<T>.value(): T {
        return first()
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun now(): Instant {
            return Instant.fromEpochMilliseconds(millis)
        }
    }
}
