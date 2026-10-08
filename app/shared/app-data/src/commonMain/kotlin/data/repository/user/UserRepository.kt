package com.wynime.app.data.repository.user

import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.app.domain.foundation.LoadError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import com.wynime.app.data.models.user.SelfInfo
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.domain.session.SessionManager
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.catching
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class UserRepository(
    private val dataStore: DataStore<SelfInfo?>,
    private val sessionStateProvider: SessionStateProvider,
    private val sessionManager: SessionManager,
    coroutineContext: CoroutineContext = Dispatchers.Default,

    private val officialBangumiApi: BangumiApiProvider,
) {
    private val logger = logger<UserRepository>()
    private val scope = CoroutineScope(coroutineContext)

    private val selfInfoRefresher = FlowRestarter()
    private val _selfInfoLoadError = MutableStateFlow<LoadError?>(null)

    val selfInfoLoadError: StateFlow<LoadError?> = _selfInfoLoadError.asStateFlow()

    val selfInfoFlow: Flow<SelfInfo?> = sessionStateProvider.stateFlow.transformLatest { state ->

            when (state) {
                is SessionState.Invalid -> {
                    _selfInfoLoadError.value = null
                    emit(null)
                }
                is SessionState.Valid -> {
                    _selfInfoLoadError.value = null
                    emit(dataStore.data.firstOrNull())
                    suspend {
                        officialBangumiApi.request { getMyself() }.toSelfInfo()
                    }
                        .asFlow()
                        .retryWhen { e, attempt ->
                            val wrapped = RepositoryException.wrapOrThrowCancellation(e)
                            val retryable = wrapped is RepositoryAuthorizationException ||
                                wrapped is RepositoryNetworkException ||
                                wrapped is RepositoryServiceUnavailableException ||
                                wrapped is RepositoryRateLimitedException
                            val shouldRetry = retryable && attempt < 3
                            if (shouldRetry) {
                                if (wrapped is RepositoryAuthorizationException && attempt == 0L) {
                                    sessionManager.refreshSession()
                                }
                                logger.warn(wrapped) {
                                    "Failed to get Bangumi user info, retrying attempt ${attempt + 1}/3"
                                }
                                delay(125L * (attempt + 1))
                            }
                            shouldRetry
                        }
                        .catching()
                        .restartable(selfInfoRefresher)
                        .collectLatest { result ->
                            result
                                .onSuccess { self ->
                                    _selfInfoLoadError.value = null
                                    coroutineScope {
                                        launch { dataStore.updateData { self } }
                                        emit(self)
                                    }
                                }
                                .onFailure { e ->
                                    val wrapped = RepositoryException.wrapOrThrowCancellation(e)
                                    _selfInfoLoadError.value = LoadError.fromException(wrapped)
                                    logger.error(wrapped) {
                                        "Failed to refresh Bangumi user profile info."
                                    }
                                }
                        }
                }
            }

            }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    suspend fun clearSelfInfo() {
        sessionManager.clearSession()
        dataStore.updateData { null }
    }
}

private fun com.wynime.datasources.bangumi.models.BangumiUser.toSelfInfo(): SelfInfo {
    val stableId = id.toString().padStart(12, '0')
    return SelfInfo(
        id = Uuid.parse("00000000-0000-0000-0000-$stableId"),
        nickname = nickname,
        email = null,
        hasPassword = false,
        avatarUrl = avatar.large,
        bangumiUsername = username,
        isBangumiSessionValid = true,
        externalAccounts = emptyList(),
    )
}

