package com.wynime.app.ui.settings.account

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.user.SelfInfoStateProducer
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.app.ui.user.TestSelfInfoUiState
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProfileViewModel : AbstractViewModel(), KoinComponent {
    private val userRepo: UserRepository by inject()
    private val selfInfoStateProvider = SelfInfoStateProducer(koin = getKoin())
    val stateFlow = selfInfoStateProvider.flow.map { state ->
        AccountSettingsState(state, state.isSessionValid == true && state.bangumiConnected == true)
    }.stateInBackground(AccountSettingsState.Empty, SharingStarted.WhileSubscribed(5_000))
    suspend fun logout() = userRepo.clearSelfInfo()
}

@Immutable
class AccountSettingsState(val selfInfo: SelfInfoUiState, val boundBangumi: Boolean) {
    companion object {
        val Empty = AccountSettingsState(SelfInfoUiState(null, true, null, null), false)
    }
}

@OptIn(TestOnly::class)
@TestOnly
val TestAccountSettingsState = AccountSettingsState(TestSelfInfoUiState, true)
