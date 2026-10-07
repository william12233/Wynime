package com.wynime.app.domain.session

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.app.data.repository.RepositoryUnknownException
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.GuestSession
import com.wynime.app.data.repository.user.Session
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.domain.session.auth.OAuthResult
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.info
import com.wynime.utils.logging.thisLogger
import com.wynime.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class SessionManager(
    private val tokenRepository: TokenRepository,
    private val coroutineScope: CoroutineScope,
    private val refreshSession: SessionRefresher,
    private val clock: Clock = Clock.System,
    private val config: Config = Config(),
) {
    fun interface SessionRefresher {

        suspend fun refresh(refreshToken: String): OAuthResult
    }

    data class Config(

        val refreshTokenBefore: Duration = 7.days,

        val refreshAttemptInterval: Duration = 1.hours,
    )

    private val logger = thisLogger()

    val sessionFlow: StateFlow<Session> = tokenRepository.session
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), initialValue = GuestSession)

    private val _stateProvider = object : SessionStateProvider {
        override val stateFlow =
            MutableSharedFlow<SessionState>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val eventFlow =
            MutableSharedFlow<SessionEvent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        suspend fun emitEvent(event: SessionEvent) {
            eventFlow.emit(event)
        }
    }

    val stateProvider get() = _stateProvider

    private val backgroundJob by lazy {
        fun emitState(state: SessionState) {
            check(_stateProvider.stateFlow.tryEmit(state))
        }

        suspend fun maintainAccessTokenLoop(session: AccessTokenSession) {
            logger.debug {
                "SessionManager: maintainAccessTokenLoop started with session: $session"
            }

            if (session.tokens.isExpired(clock)) {

                try {

                    refreshSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RepositoryException) {

                    val reason = when (e) {
                        is RepositoryAuthorizationException -> {

                            clearSession()
                            InvalidSessionReason.NO_TOKEN
                        }

                        is RepositoryNetworkException -> InvalidSessionReason.NETWORK_ERROR

                        is RepositoryRateLimitedException -> InvalidSessionReason.NETWORK_ERROR

                        is RepositoryServiceUnavailableException -> InvalidSessionReason.NO_TOKEN
                        is RepositoryUnknownException -> InvalidSessionReason.UNKNOWN
                        is RepositoryRequestError -> InvalidSessionReason.UNKNOWN
                    }

                    if (reason == InvalidSessionReason.UNKNOWN) {
                        logger.error("Refresh session failed with unknown error", e)
                    } else {

                        logger.warn { "Refresh session failed with known error: $reason" }
                    }

                    emitState(SessionState.Invalid(reason))
                } catch (e: Exception) {
                    emitState(SessionState.Invalid(InvalidSessionReason.UNKNOWN))
                    logger.error("Refresh session failed", e)
                }
            } else {

                emitState(SessionState.Valid(bangumiConnected = session.tokens.bangumiAccessToken != null))

                val ttl = (session.tokens.expiresAtMillis - clock.now().toEpochMilliseconds()).milliseconds
                    .minus(config.refreshTokenBefore)

                logger.debug {
                    "SessionManager: access token is valid, will refresh in $ttl ms"
                }

                delay(ttl)

                logger.info {
                    "SessionManager: access token is about to expire, refreshing now"
                }

                while (session.tokens.isExpired(clock)) {
                    try {
                        refreshSession()
                    } catch (e: Exception) {

                        val re = RepositoryException.wrapOrThrowCancellation(e)
                        if (re is RepositoryUnknownException) {
                            logger.error(
                                "Refresh session failed with unknown exception, see cause. Retrying in ${config.refreshAttemptInterval}",
                                e,
                            )
                        } else {
                            logger.warn("Refresh session failed with $re. Retrying in ${config.refreshAttemptInterval}")
                        }
                        delay(config.refreshAttemptInterval)
                    }
                }
            }
        }

        coroutineScope.launch(CoroutineName("SessionManager auto refresh")) {
            sessionFlow.collectLatest { session ->
                when (session) {
                    is GuestSession -> emitState(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
                    is AccessTokenSession -> maintainAccessTokenLoop(session)
                }
            }
        }

        Unit
    }

    fun startBackgroundJob() {
        backgroundJob
    }

    suspend fun clearSessionIfAccessTokenExpired() {
        val session = tokenRepository.session.first()
        if (session is AccessTokenSession && session.tokens.isExpired(clock)) {
            logger.info { "SessionManager: saved access token is expired on startup, clearing session" }
            clearSession()
        }
    }

    suspend fun setSession(
        session: AccessTokenSession,

        refreshToken: String,
        isNewLogin: Boolean = true,
    ) {
        tokenRepository.setSession(session)
        tokenRepository.setRefreshToken(refreshToken)
        if (isNewLogin) {
            _stateProvider.emitEvent(SessionEvent.NewLogin)
        }
    }

    suspend fun clearSession() {
        tokenRepository.clear()

    }

    private val refreshSessionLock = Mutex()

    suspend fun refreshSession() = refreshSessionLock.withLock {
        val refreshToken = tokenRepository.refreshToken.first() ?: return@withLock

        try {
            val result = refreshSession.refresh(refreshToken)
            setSession(
                session = AccessTokenSession(
                    tokens = result.tokens,
                ),
                refreshToken = result.refreshToken,
                isNewLogin = false,
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {

            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
