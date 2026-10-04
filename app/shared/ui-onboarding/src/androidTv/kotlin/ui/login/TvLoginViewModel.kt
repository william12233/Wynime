/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import me.him188.ani.app.data.network.WynimeCloudClient
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.auth.OAuthConfigurator
import me.him188.ani.app.domain.session.auth.WynimeBangumiOAuthClient
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

/** Coordinates the TV Bangumi OAuth flow and exposes only presentation-safe state. */
class TvLoginViewModel(
    koin: Koin,
) : AbstractViewModel() {
    private val oauthConfigurator = OAuthConfigurator(
        client = WynimeBangumiOAuthClient(koin.get<WynimeCloudClient>()),
        sessionManager = koin.get<SessionManager>(),
        sessionStateProvider = koin.get<SessionStateProvider>(),
    )
    private val fields = MutableStateFlow(TvLoginUiState())
    private val requestMutex = Mutex()
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    val uiState: StateFlow<TvLoginUiState> = fields.asStateFlow()

    fun onIntent(intent: TvLoginIntent) {
        when (intent) {
            TvLoginIntent.Authorize -> Unit
        }
    }

    fun startOAuth(onOpenUrl: suspend (String) -> Unit) {
        if (!requestMutex.tryLock()) return
        fields.update { it.copy(busy = true, error = null) }
        backgroundScope.launch {
            try {
                when (oauthConfigurator.auth(isRegister = false, onOpenUrl = onOpenUrl)) {
                    is OAuthConfigurator.State.Success -> navigation.emit(TvNavigationEvent.LoggedIn)
                    is OAuthConfigurator.State.Failed -> fields.update {
                        it.copy(error = "Bangumi 登入失敗，請重試")
                    }
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fields.update { it.copy(error = "Bangumi 登入失敗，請重試") }
            } finally {
                fields.update { it.copy(busy = false) }
                requestMutex.unlock()
            }
        }
    }
}
