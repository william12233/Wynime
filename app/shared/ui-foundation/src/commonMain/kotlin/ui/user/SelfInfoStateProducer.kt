package com.wynime.app.ui.user

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.wynime.app.data.models.user.SelfInfo
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

@Immutable
data class SelfInfoUiState(
    val selfInfo: SelfInfo?,
    val isLoading: Boolean,

    val isSessionValid: Boolean?,

    val bangumiConnected: Boolean?
)

@TestOnly
val TestSelfInfoUiState
    get() = SelfInfoUiState(
        SelfInfo(
            id = Uuid.random(),
            nickname = "TestUser",
            email = "test@example.test",
            hasPassword = false,
            avatarUrl = null,
            bangumiUsername = "TestBangumiUser",
            isBangumiSessionValid = true,
        ),
        isLoading = false,
        isSessionValid = true,
        bangumiConnected = true,
    )

class SelfInfoStateProducer(
    flowContext: CoroutineContext = Dispatchers.Default,
    koin: Koin = GlobalKoin,
) {
    private val sessionStateProvider: SessionStateProvider by koin.inject()
    private val userRepository: UserRepository by koin.inject()

    val flow = combine(sessionStateProvider.stateFlow, userRepository.selfInfoFlow) { sessionState, selfInfo ->
        val isSessionValid = sessionState is SessionState.Valid
        SelfInfoUiState(
            selfInfo = if (isSessionValid) selfInfo else null,
            isLoading = false,
            isSessionValid = isSessionValid,
            bangumiConnected = isSessionValid && sessionState.bangumiConnected,
        )
    }.stateIn(
        CoroutineScope(flowContext),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SelfInfoUiState(
            selfInfo = null,
            isLoading = true,
            isSessionValid = null,
            bangumiConnected = null,
        ),
    )
}
