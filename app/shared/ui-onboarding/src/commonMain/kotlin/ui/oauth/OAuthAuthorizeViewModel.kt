package com.wynime.app.ui.oauth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import com.wynime.app.data.network.WynimeCloudClient
import com.wynime.app.domain.session.SessionEvent
import com.wynime.app.domain.session.SessionManager
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.domain.session.auth.OAuthConfigurator
import com.wynime.app.domain.session.auth.OAuthPlatform
import com.wynime.app.domain.session.auth.WynimeBangumiOAuthClient
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.utils.coroutines.SingleTaskExecutor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OAuthAuthorizeViewModel(
    requestedPlatform: OAuthPlatform,
) : AbstractViewModel(), KoinComponent {

    val platform: OAuthPlatform = OAuthPlatform.BANGUMI

    private val wynimeCloudClient: WynimeCloudClient by inject()
    private val sessionManager: SessionManager by inject()
    private val sessionStateProvider: SessionStateProvider by inject()

    private val tasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    private val configurator = OAuthConfigurator(
        client = WynimeBangumiOAuthClient(wynimeCloudClient),
        sessionManager = sessionManager,
        sessionStateProvider = sessionStateProvider,
    )

    val state: Flow<AuthState> =
        combine(sessionStateProvider.stateFlow, configurator.state) { sessionState, authState ->
            when (authState) {
                is OAuthConfigurator.State.Idle -> {
                    if (sessionState is SessionState.Valid) {
                        AuthState.LoggedInBangumi(sessionState.bangumiConnected)
                    } else {
                        AuthState.NoBangumiAccount
                    }
                }

                is OAuthConfigurator.State.AwaitingResult -> AuthState.AwaitingResult
                is OAuthConfigurator.State.Failed -> AuthState.Failed(
                    authState.error,
                    sessionState is SessionState.Valid && sessionState.bangumiConnected,
                )

                is OAuthConfigurator.State.Success -> {
                    if (sessionState is SessionState.Valid) {
                        AuthState.Success
                    } else {
                        AuthState.AwaitingResult
                    }
                }
            }
        }

    init {
        check(requestedPlatform == OAuthPlatform.BANGUMI) {
            "Only Bangumi OAuth is supported"
        }
    }

    suspend fun doOAuth(isRegister: Boolean, onOpenUrl: suspend (String) -> Unit): Boolean {
        val res = tasker.invoke {
            configurator.auth(isRegister, onOpenUrl)
        }
        return res is OAuthConfigurator.State.Success
    }

    suspend fun collectNewLoginEvent(block: () -> Unit) {
        sessionManager.stateProvider
            .eventFlow
            .filterIsInstance<SessionEvent.NewLogin>()
            .collect { block() }
    }

    fun cancelCurrentOAuth() {
        tasker.cancelCurrent()
    }
}
