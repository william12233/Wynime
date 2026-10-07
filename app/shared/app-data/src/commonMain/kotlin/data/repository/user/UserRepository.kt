package com.wynime.app.data.repository.user

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
import com.wynime.app.data.repository.RepositoryRequestError
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

    val selfInfoFlow: Flow<SelfInfo?> = sessionStateProvider.stateFlow.transformLatest { state ->

            when (state) {
                is SessionState.Invalid -> emit(null)
                is SessionState.Valid -> {
                    emit(dataStore.data.firstOrNull())
                    suspend {
                        officialBangumiApi.request { getMyself() }.toSelfInfo()
                    }
                        .asFlow()
                        .retryWhen { e, attempt ->
                            val wrapped = RepositoryException.wrapOrThrowCancellation(e)
                            (wrapped is RepositoryAuthorizationException && attempt < 3).also {
                                if (it) {
                                    logger.warn(wrapped) { "Failed to get Bangumi user info, retried $attempt, max retries: 3" }
                                    delay(125L)
                                }
                            }
                        }
                        .catching()
                        .restartable(selfInfoRefresher)
                        .collectLatest { result ->
                            result
                                .onSuccess { self ->
                                    coroutineScope {
                                        launch { dataStore.updateData { self } }
                                        emit(self)
                                    }
                                }
                                .onFailure { e ->
                                    logger.error(RepositoryException.wrapOrThrowCancellation(e)) {
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

