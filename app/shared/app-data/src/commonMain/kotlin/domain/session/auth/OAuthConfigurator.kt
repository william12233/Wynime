package com.wynime.app.domain.session.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.session.SessionManager
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.Uuid
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class OAuthConfigurator(
    private val client: OAuthClient,
    private val sessionManager: SessionManager,
    private val sessionStateProvider: SessionStateProvider,
    private val random: Random = Random.Default,
) {
    private val logger = logger<OAuthConfigurator>()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    suspend fun auth(isRegister: Boolean, onOpenUrl: suspend (String) -> Unit): State {
        val requestId = Uuid.random(random).toString()
        val tokenDeferred = CompletableDeferred<OAuthResult>()

        logger.info { "OAuth started, request id: $requestId" }
        _state.value = State.AwaitingResult(requestId, tokenDeferred)

        try {
            val externalUrl = if (!isRegister) {
                logger.info { "Request bind, request id: $requestId" }
                client.getOAuthBindLink(requestId)
            } else {
                logger.info { "Request register, request id: $requestId" }
                client.getOAuthRegisterLink(requestId)
            }

            onOpenUrl(externalUrl)

            var oAuthResult: OAuthResult? = null
            while (oAuthResult == null) {
                delay(1.seconds)
                oAuthResult = client.getResult(requestId)

            }

            _state.value = State.Success(requestId, oAuthResult)
            logger.info {
                "Oauth success, request id: $requestId, " +
                        "token hash: ${oAuthResult.tokens.legacyServiceAccessToken.hashCode()}"
            }

            sessionManager.setSession(
                AccessTokenSession(oAuthResult.tokens),
                oAuthResult.refreshToken,
            )
        } catch (ex: CancellationException) {
            _state.value = State.Idle
            throw ex
        } catch (ex: Exception) {
            val re = RepositoryException.wrapOrThrowCancellation(ex)
            val loadError = LoadError.fromException(re)
            if (loadError is LoadError.UnknownError) {
                logger.error(re) { "OAuth failed with unknown error, request id: $requestId" }
            } else {
                logger.warn { "OAuth failed, request id: $requestId, $loadError" }
            }
            _state.value = State.Failed(loadError)
        }
        return _state.value
    }

    sealed interface State {
        data object Idle : State
        class AwaitingResult(val requestId: String, val deferred: CompletableDeferred<OAuthResult>) : State
        class Success(val requestId: String, val result: OAuthResult) : State
        class Failed(val error: LoadError) : State
    }
}
