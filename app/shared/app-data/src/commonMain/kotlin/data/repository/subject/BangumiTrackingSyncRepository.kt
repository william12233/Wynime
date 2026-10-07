package com.wynime.app.data.repository.subject

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wynime.app.data.persistent.database.dao.BangumiTrackingAccountEntity
import com.wynime.app.data.persistent.database.dao.BangumiTrackingMetadataDao
import com.wynime.app.data.persistent.database.dao.BangumiTrackingMetadataEntity
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.RepositoryException.Companion.wrapOrThrowCancellation
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.network.SubjectService
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Instant

data class BangumiTrackingAccount(
    val id: Int,
    val username: String,
) {
    val key: String get() = "id:$id"
}

enum class BangumiTrackingPendingOperation {
    UPSERT_COLLECTION,
    DELETE_COLLECTION,
}

class BangumiTrackingMetadataRepository(
    private val dao: BangumiTrackingMetadataDao,
    private val tokenRepository: TokenRepository,
    private val accountBindingStore: BangumiTrackingSyncSettingsStore? = null,
) {
    private var adoptedAccountKey: String? = null
    private var adoptedTokenKey: String? = null

    suspend fun accountKey(): String {
        val tokenKey = currentTokenKey()
        if (tokenKey == adoptedTokenKey) return adoptedAccountKey ?: tokenKey
        return accountBindingStore?.accountKeyForToken(tokenKey) ?: tokenKey
    }

    suspend fun adoptAccount(account: BangumiTrackingAccount): String {
        val tokenKey = currentTokenKey()
        val oldKey = if (tokenKey == adoptedTokenKey) {
            adoptedAccountKey ?: tokenKey
        } else {
            accountBindingStore?.accountKeyForToken(tokenKey) ?: tokenKey
        }
        val persistedAccount = dao.findAccountByUserId(account.id)
        val sourceKey = persistedAccount?.accountKey ?: oldKey
        if (sourceKey != account.key) {
            val oldMetadata = dao.list(sourceKey)
            if (oldMetadata.isNotEmpty()) {
                dao.upsertAll(oldMetadata.map { it.copy(accountKey = account.key) })
            }
            dao.findAccount(sourceKey)?.let { old ->
                dao.upsertAccount(old.copy(accountKey = account.key, userId = account.id, username = account.username))
            }
            dao.deleteAll(sourceKey)
            dao.deleteAccount(sourceKey)
        }
        dao.upsertAccount(
            BangumiTrackingAccountEntity(
                accountKey = account.key,
                userId = account.id,
                username = account.username,
            ),
        )
        adoptedAccountKey = account.key
        adoptedTokenKey = tokenKey
        accountBindingStore?.bindTokenToAccount(tokenKey, account.key)
        return account.key
    }

    private suspend fun currentTokenKey(): String {
        val refreshToken = tokenRepository.refreshToken.first()
        val session = tokenRepository.session.first()
        val bangumiToken = (session as? AccessTokenSession)?.tokens?.bangumiAccessToken
        return (refreshToken ?: bangumiToken)
            ?.takeIf(String::isNotBlank)
            ?.let { "token:${it.hashCode().toUInt().toString(16)}" }
            ?: GUEST_ACCOUNT_KEY
    }

    suspend fun find(subjectId: Int): BangumiTrackingMetadataEntity? = dao.find(accountKey(), subjectId)

    suspend fun markLocalChange(
        subjectId: Int,
        type: UnifiedCollectionType,
        modifiedAt: Long,
    ) {
        val key = accountKey()
        val old = dao.find(key, subjectId)
        dao.upsert(
            (old ?: BangumiTrackingMetadataEntity(accountKey = key, subjectId = subjectId)).copy(
                localDeletedAt = null,
                lastLocalModifiedAt = modifiedAt,
                pendingType = type.name,
                pendingOperation = BangumiTrackingPendingOperation.UPSERT_COLLECTION.name,
                pendingError = null,
            ),
        )
    }

    suspend fun markLocalDeletion(subjectId: Int, deletedAt: Long) {
        val key = accountKey()
        val old = dao.find(key, subjectId)
        dao.upsert(
            (old ?: BangumiTrackingMetadataEntity(accountKey = key, subjectId = subjectId)).copy(
                localDeletedAt = deletedAt,
                lastLocalModifiedAt = deletedAt,
                pendingType = null,
                pendingOperation = BangumiTrackingPendingOperation.DELETE_COLLECTION.name,
                pendingError = null,
            ),
        )
    }

    suspend fun markRemoteObserved(
        subjectId: Int,
        remoteUpdatedAt: Long?,
        remoteType: UnifiedCollectionType? = null,
        syncedAt: Long = currentTimeMillis(),
        clearPending: Boolean = false,
    ) {
        val key = accountKey()
        val old = dao.find(key, subjectId)
        dao.upsert(
            (old ?: BangumiTrackingMetadataEntity(accountKey = key, subjectId = subjectId)).copy(
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedAt = syncedAt,
                lastSyncedType = remoteType?.name,
                localDeletedAt = if (clearPending) null else old?.localDeletedAt,
                pendingType = if (clearPending) null else old?.pendingType,
                pendingOperation = if (clearPending) null else old?.pendingOperation,
                pendingError = if (clearPending) null else old?.pendingError,
            ),
        )
    }

    suspend fun clearTombstone(
        subjectId: Int,
        remoteUpdatedAt: Long? = null,
        remoteType: UnifiedCollectionType? = null,
    ) {
        val key = accountKey()
        val old = dao.find(key, subjectId) ?: return
        dao.upsert(
            old.copy(
                localDeletedAt = null,
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedType = remoteType?.name,
                lastSyncedAt = currentTimeMillis(),
                pendingType = null,
                pendingOperation = null,
                pendingError = null,
            ),
        )
    }

    suspend fun markMutationSucceeded(
        subjectId: Int,
        remoteType: UnifiedCollectionType?,
        remoteUpdatedAt: Long?,
        syncedAt: Long = currentTimeMillis(),
        expectedAccountKey: String? = null,
    ) {
        val key = expectedAccountKey ?: accountKey()
        val old = dao.find(key, subjectId)
        dao.upsert(
            (old ?: BangumiTrackingMetadataEntity(accountKey = key, subjectId = subjectId)).copy(
                localDeletedAt = null,
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedType = remoteType?.name,
                lastSyncedAt = syncedAt,
                pendingType = null,
                pendingOperation = null,
                pendingError = null,
            ),
        )
    }

    suspend fun markPendingError(subjectId: Int, error: Throwable, expectedAccountKey: String? = null) {
        val key = expectedAccountKey ?: accountKey()
        val old = dao.find(key, subjectId) ?: return
        dao.upsert(old.copy(pendingError = error.message ?: error::class.simpleName))
    }

    suspend fun metadataFor(accountKey: String): List<BangumiTrackingMetadataEntity> = dao.list(accountKey)

    suspend fun saveAccountSummary(
        accountKey: String,
        lastSuccessfulSyncAt: Long,
        localCount: Int,
        remoteCount: Int,
    ) {
        val account = dao.findAccount(accountKey)
        dao.upsertAccount(
            (account ?: BangumiTrackingAccountEntity(accountKey)).copy(
                lastSuccessfulSyncAt = lastSuccessfulSyncAt,
                localCount = localCount,
                remoteCount = remoteCount,
            ),
        )
    }

    suspend fun accountSummary(accountKey: String): BangumiTrackingAccountEntity? = dao.findAccount(accountKey)

    companion object {
        const val GUEST_ACCOUNT_KEY = "guest"
    }
}

data class BangumiTrackingSyncResult(
    val localUpdated: Int,
    val bangumiUpdated: Int,
    val unchanged: Int,
    val conflictsResolved: Int,
    val deletedRemote: Int = 0,
    val fetchedCollectionCount: Int = 0,
    val expectedCollectionCount: Int? = null,
    val fetchedEpisodeSubjectCount: Int = 0,
    val episodeCount: Int = 0,
    val episodeUpdated: Int = 0,
    val failedSubjectIds: List<Int> = emptyList(),
    val failureMessages: List<String> = emptyList(),
    val elapsedMillis: Long = 0,
)

data class BangumiTrackingSyncSummary(
    val lastSuccessfulSyncAt: Long?,
    val localCount: Int,
    val remoteCount: Int,
)

sealed interface BangumiTrackingAutoSyncEvent {
    data class Succeeded(
        val subjectId: Int,
        val type: UnifiedCollectionType,
    ) : BangumiTrackingAutoSyncEvent

    data class Failed(
        val subjectId: Int,
        val error: BangumiTrackingConnectionError,
    ) : BangumiTrackingAutoSyncEvent

    data class Deleted(
        val subjectId: Int,
    ) : BangumiTrackingAutoSyncEvent
}

sealed interface BangumiTrackingConnectionResult {
    data class Connected(val account: BangumiTrackingAccount) : BangumiTrackingConnectionResult
    data object NotConnected : BangumiTrackingConnectionResult
    data class Failed(val error: BangumiTrackingConnectionError) : BangumiTrackingConnectionResult
}

enum class BangumiTrackingConnectionError {
    AUTHORIZATION,
    NETWORK,
    RATE_LIMITED,
    UNKNOWN,
}

fun classifyBangumiTrackingError(throwable: Throwable): BangumiTrackingConnectionError {
    val normalized = wrapOrThrowCancellation(throwable)
    return when (normalized) {
        is RepositoryAuthorizationException -> BangumiTrackingConnectionError.AUTHORIZATION
        is RepositoryRateLimitedException -> BangumiTrackingConnectionError.RATE_LIMITED
        is com.wynime.app.data.repository.RepositoryNetworkException ->
            BangumiTrackingConnectionError.NETWORK
        is RepositoryException -> BangumiTrackingConnectionError.UNKNOWN
    }
}

interface BangumiTrackingSyncEnqueuer {
    fun enqueueLocalChange(subjectId: Int)
}

class BangumiTrackingSyncRepository(
    private val subjectCollectionDao: SubjectCollectionDao,
    private val metadataDao: BangumiTrackingMetadataDao,
    private val metadataRepository: BangumiTrackingMetadataRepository,
    private val subjectService: SubjectService,
    private val bangumiApi: BangumiTrackingSyncApi,
    private val settingsStore: BangumiTrackingSyncSettingsStore,
    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val syncCoordinator: BangumiSyncCoordinator = BangumiSyncCoordinator(),
) : BangumiTrackingSyncEnqueuer {
    private val syncLock = Mutex()
    private var runningSync: kotlinx.coroutines.Deferred<BangumiTrackingSyncResult>? = null
    private val pendingJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    private val pendingLock = Mutex()
    private val _autoSyncEvents = MutableSharedFlow<BangumiTrackingAutoSyncEvent>(extraBufferCapacity = 16)
    val autoSyncEvents = _autoSyncEvents.asSharedFlow()
    val syncState: kotlinx.coroutines.flow.StateFlow<BangumiSyncUiState> = syncCoordinator.state

    suspend fun summary(): BangumiTrackingSyncSummary? {
        val accountKey = metadataRepository.accountKey()
        return metadataRepository.accountSummary(accountKey)?.let {
            BangumiTrackingSyncSummary(it.lastSuccessfulSyncAt, it.localCount, it.remoteCount)
        }
    }

    override fun enqueueLocalChange(subjectId: Int) {
        serviceScope.launch {
            pendingLock.withLock {
                pendingJobs.remove(subjectId)?.cancel()
                pendingJobs[subjectId] = serviceScope.launch {

                    yield()
                    flushPending(subjectId)
                }
            }
        }
    }

    suspend fun testConnection(): BangumiTrackingConnectionResult {
        return try {
            val user = currentBangumiUser() ?: return BangumiTrackingConnectionResult.NotConnected
            metadataRepository.adoptAccount(user)
            BangumiTrackingConnectionResult.Connected(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            BangumiTrackingConnectionResult.Failed(e.toConnectionError())
        }
    }

    suspend fun retryPendingChanges(account: BangumiTrackingAccount) {
        if (!settingsStore.flow.first().autoSyncTracking) return
        val accountKey = metadataRepository.adoptAccount(account)
        metadataDao.list(accountKey)
            .asSequence()
            .filter {
                it.pendingOperation != null || it.pendingType != null || it.localDeletedAt != null
            }
            .map { it.subjectId }
            .forEach(::enqueueLocalChange)
    }

    suspend fun syncNow(): BangumiTrackingSyncResult {
        val deferred = syncLock.withLock {
            runningSync ?: serviceScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                syncCoordinator.withExclusive(BangumiSyncOperation.TRACKING) {
                    performFullSync()
                }
            }.also { runningSync = it }
        }
        return try {
            deferred.await()
        } finally {
            syncLock.withLock {
                if (runningSync === deferred) runningSync = null
            }
        }
    }

    suspend fun confirmPendingWebRemovals() {
        try {
            if (metadataDao.list(metadataRepository.accountKey()).none {
                    it.pendingOperation == BangumiTrackingPendingOperation.DELETE_COLLECTION.name
                }) return
            val account = currentBangumiUser() ?: return
            val key = metadataRepository.adoptAccount(account)
            metadataDao.list(key).filter {
                it.pendingOperation == BangumiTrackingPendingOperation.DELETE_COLLECTION.name
            }.forEach { flushPending(it.subjectId, confirmWebRemoval = true) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logger.warn(error) { "Pending collection removal verification failed; retaining its state" }
        }
    }

    private suspend fun flushPending(subjectId: Int, confirmWebRemoval: Boolean = false) {
        var attemptedOperation: BangumiTrackingPendingOperation? = null
        var accountKey: String? = null
        try {
            val settings = settingsStore.flow.first()
            if (!settings.autoSyncTracking && !confirmWebRemoval) return
            val user = currentBangumiUser() ?: return
            val resolvedAccountKey = metadataRepository.adoptAccount(user)
            accountKey = resolvedAccountKey
            val metadata = metadataDao.find(resolvedAccountKey, subjectId) ?: return
            val operation = metadata.pendingOperation
                ?.let { runCatching { BangumiTrackingPendingOperation.valueOf(it) }.getOrNull() }
                ?: when {
                    metadata.localDeletedAt != null -> BangumiTrackingPendingOperation.DELETE_COLLECTION
                    metadata.pendingType != null -> BangumiTrackingPendingOperation.UPSERT_COLLECTION
                    else -> return
                }
            attemptedOperation = operation
            if (confirmWebRemoval && operation != BangumiTrackingPendingOperation.DELETE_COLLECTION) return
            syncCoordinator.withExclusive(BangumiSyncOperation.AUTO) {
                when (operation) {
                    BangumiTrackingPendingOperation.DELETE_COLLECTION -> {
                        ensureRemovalAccount(resolvedAccountKey, user.username)
                        val before = readRemoteCollection(user.username, subjectId)
                        if (before != null) {
                            timedRemoteMutation("POST", "/api/v1/collections/{subjectId}/removal/confirm") {
                                subjectService.deleteSubjectCollection(subjectId)
                            }
                        }
                        val after = readRemoteCollection(user.username, subjectId)
                        check(after == null) {
                            "Bangumi collection still exists after DELETE_COLLECTION"
                        }
                        ensureRemovalAccount(resolvedAccountKey, user.username)
                        val latest = metadataDao.find(resolvedAccountKey, subjectId)
                        check(latest?.pendingOperation == metadata.pendingOperation &&
                            latest?.localDeletedAt == metadata.localDeletedAt
                        ) { "Collection operation changed during removal verification" }
                        markVerifiedLocalRemoval(subjectId)
                        metadataRepository.markMutationSucceeded(
                            subjectId = subjectId,
                            remoteType = null,
                            remoteUpdatedAt = null,
                            expectedAccountKey = resolvedAccountKey,
                        )
                        _autoSyncEvents.tryEmit(BangumiTrackingAutoSyncEvent.Deleted(subjectId))
                    }

                    BangumiTrackingPendingOperation.UPSERT_COLLECTION -> {
                        val type = metadata.pendingType
                            ?.let { runCatching { UnifiedCollectionType.valueOf(it) }.getOrNull() }
                            ?.takeUnless { it == UnifiedCollectionType.NOT_COLLECTED }
                            ?: throw RepositoryRequestError(
                                "Invalid UPSERT_COLLECTION pending type for subject=$subjectId",
                            )
                        timedRemoteMutation("POST", "/v0/users/{username}/collections/{subjectId}") {
                            upsertRemoteType(subjectId, type)
                        }
                        val after = readRemoteCollection(user.username, subjectId)
                        check(after?.type == type) {
                            "Bangumi collection verification mismatch after UPSERT_COLLECTION"
                        }
                        metadataRepository.markMutationSucceeded(
                            subjectId = subjectId,
                            remoteType = after.type,
                            remoteUpdatedAt = after.updatedAt,
                        )
                        _autoSyncEvents.tryEmit(BangumiTrackingAutoSyncEvent.Succeeded(subjectId, type))
                    }
                }
                val previousSummary = metadataRepository.accountSummary(resolvedAccountKey)
                metadataRepository.saveAccountSummary(
                    accountKey = resolvedAccountKey,
                    lastSuccessfulSyncAt = currentTimeMillis(),
                    localCount = previousSummary?.localCount
                        ?: subjectCollectionDao.listAll().count { it.collectionType != UnifiedCollectionType.NOT_COLLECTED },
                    remoteCount = previousSummary?.remoteCount ?: 0,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val latest = accountKey?.let { metadataDao.find(it, subjectId) }
            val attemptedOperationName = attemptedOperation?.name
            val isCurrentAttempt = attemptedOperation == null ||
                latest?.pendingOperation == attemptedOperationName ||
                (attemptedOperation == BangumiTrackingPendingOperation.DELETE_COLLECTION &&
                    latest?.localDeletedAt != null)
            if (isCurrentAttempt) {
                metadataRepository.markPendingError(subjectId, e, accountKey)
                _autoSyncEvents.tryEmit(
                    BangumiTrackingAutoSyncEvent.Failed(subjectId, classifyBangumiTrackingError(e)),
                )
            }
        } finally {
            val currentJob = coroutineContext[Job]
            pendingLock.withLock {
                if (pendingJobs[subjectId] === currentJob) {
                    pendingJobs.remove(subjectId)
                }
            }
        }
    }

    private suspend fun performFullSync(): BangumiTrackingSyncResult {
        val startedAt = currentTimeMillis()
        val user = currentBangumiUser() ?: throw RepositoryAuthorizationException("Bangumi login is required")
        val account = user
        val accountKey = metadataRepository.adoptAccount(account)
        val settings = settingsStore.flow.first()
        val local = subjectCollectionDao.listAll()
            .associateBy { it.subjectId }
            .mapValues { (_, value) ->
                BangumiTrackingLocalSnapshot(value.subjectId, value.collectionType, value.lastUpdated)
            }
        val metadata = metadataRepository.metadataFor(accountKey).associateBy { it.subjectId }
        val fetched = fetchAllCollections(user.username)
        val remote = fetched.items.associateBy { it.subjectId }
        val subjectIds = (local.keys + remote.keys + metadata.keys).toSortedSet()
        logger.info {
            "BangumiSync start strategy=${settings.conflictPolicy} local=${local.size} " +
                "remote=${remote.size} baseline=${metadata.count { it.value.lastSyncedAt > 0 }} " +
                "pending=${metadata.count { it.value.pendingOperation != null || it.value.localDeletedAt != null }}"
        }
        logger.info {
            "BangumiSync fetched remote=${remote.size} pullRequests=${fetched.requestCount}"
        }
        val plans = subjectIds.map { subjectId ->
            val metadataRow = metadata[subjectId]
            if (metadataRow?.pendingOperation == BangumiTrackingPendingOperation.DELETE_COLLECTION.name) {
                return@map BangumiTrackingSyncPlan(subjectId, BangumiTrackingSyncAction.DeleteRemote, conflict = false)
            }
            BangumiTrackingSyncEngine.plan(
                policy = settings.conflictPolicy,
                local = local[subjectId],
                remote = remote[subjectId],
                tombstone = metadataRow?.localDeletedAt?.let(::BangumiTrackingTombstoneSnapshot),
                baseline = metadataRow
                    ?.takeIf { it.lastSyncedAt > 0 }
                    ?.let {
                        BangumiTrackingBaselineSnapshot(
                            type = it.lastSyncedType?.let { name ->
                                runCatching { UnifiedCollectionType.valueOf(name) }.getOrNull()
                            },
                            syncedAt = it.lastSyncedAt,
                            remoteUpdatedAt = it.remoteUpdatedAt,
                        )
                    },
            )
        }
        plans.filter { it.action !is BangumiTrackingSyncAction.NoOp }.forEach { plan ->
            val localState = local[plan.subjectId]?.type?.name ?: "ABSENT"
            val remoteState = remote[plan.subjectId]?.type?.name ?: "NOT_COLLECTED"
            val localModifiedAt = local[plan.subjectId]?.lastUpdated
            val remoteUpdatedAt = remote[plan.subjectId]?.updatedAt
            logger.debug {
                "BangumiSync change subjectId=${plan.subjectId} localState=$localState " +
                    "remoteState=$remoteState localModifiedAt=$localModifiedAt " +
                    "remoteUpdatedAt=$remoteUpdatedAt winner=${plan.action::class.simpleName} " +
                    "conflict=${plan.conflict}"
            }
        }

        syncCoordinator.report(
            operation = BangumiSyncOperation.TRACKING,
            phase = BangumiSyncPhase.MERGING,
            current = 0,
            total = subjectIds.size,
        )
        val mutationResults = coroutineScope {
            plans.filter {
                it.action is BangumiTrackingSyncAction.UpsertRemote ||
                    it.action is BangumiTrackingSyncAction.DeleteRemote
            }.map { plan ->
                async {
                    try {
                        executeRemoteMutation(user.username, accountKey, plan)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        metadataRepository.markPendingError(plan.subjectId, e, accountKey)
                        MutationResult.Failure(plan.subjectId, e)
                    }
                }
            }.awaitAll()
        }
        val failedSubjectIds = mutationResults.filterIsInstance<MutationResult.Failure>()
            .map { it.subjectId }
            .toMutableSet()
        val failureMessages = mutationResults.filterIsInstance<MutationResult.Failure>()
            .mapNotNull { it.error.message }
            .toMutableList()
        var localUpdated = 0
        val bangumiUpdated = mutationResults.count { it is MutationResult.UpsertSuccess }
        val deletedRemote = mutationResults.count { it is MutationResult.DeleteSuccess }
        val conflictsResolved = plans.count {
            it.conflict && it.action !is BangumiTrackingSyncAction.Conflict
        }
        var unchanged = 0

        for (index in plans.indices) {
            val plan = plans[index]
            syncCoordinator.report(
                operation = BangumiSyncOperation.TRACKING,
                phase = BangumiSyncPhase.APPLYING_LOCAL,
                current = index + 1,
                total = plans.size,
                failedCount = failedSubjectIds.size,
            )
            when (val action = plan.action) {
                BangumiTrackingSyncAction.NoOp -> {
                    unchanged++
                    remote[plan.subjectId].let { remoteSubject ->
                        metadataRepository.markRemoteObserved(
                            subjectId = plan.subjectId,
                            remoteUpdatedAt = remoteSubject?.updatedAt,
                            remoteType = remoteSubject?.type,
                            clearPending = true,
                        )
                    }
                }

                is BangumiTrackingSyncAction.ApplyRemote -> {
                    val remoteSubject = remote[plan.subjectId] ?: continue
                    try {
                        applyRemoteType(plan.subjectId, action.type, remoteSubject.updatedAt)
                        localUpdated++
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        failedSubjectIds += plan.subjectId
                        e.message?.let(failureMessages::add)
                    }
                }

                BangumiTrackingSyncAction.MarkLocalUntracked -> {
                    val deletedAt = currentTimeMillis()
                    subjectCollectionDao.updateType(
                        subjectId = plan.subjectId,
                        collectionType = UnifiedCollectionType.NOT_COLLECTED,
                        lastUpdated = deletedAt,
                        lastFetched = deletedAt,
                    )
                    metadataRepository.markRemoteObserved(
                        subjectId = plan.subjectId,
                        remoteUpdatedAt = null,
                        remoteType = null,
                        syncedAt = deletedAt,
                        clearPending = true,
                    )
                    localUpdated++
                }

                is BangumiTrackingSyncAction.UpsertRemote -> {
                    if (mutationResults.any { it is MutationResult.UpsertSuccess && it.subjectId == plan.subjectId }) {

                    }
                }

                BangumiTrackingSyncAction.DeleteRemote -> {

                }

                BangumiTrackingSyncAction.Conflict -> {
                    failedSubjectIds += plan.subjectId
                    failureMessages += "subject=${plan.subjectId} 兩側同時變更但沒有可判定的勝者"
                }
            }
        }
        val complete = failedSubjectIds.isEmpty()
        if (complete) {
            metadataRepository.saveAccountSummary(
                accountKey = accountKey,
                lastSuccessfulSyncAt = currentTimeMillis(),
                localCount = subjectCollectionDao.listAll().count {
                    it.collectionType != UnifiedCollectionType.NOT_COLLECTED
                },
                remoteCount = remote.size,
            )
            syncCoordinator.report(
                operation = BangumiSyncOperation.TRACKING,
                phase = BangumiSyncPhase.RELOADING,
                current = remote.size,
                total = remote.size,
            )
            syncCoordinator.complete(BangumiSyncOperation.TRACKING)
        } else {
            syncCoordinator.partialFailure(
                operation = BangumiSyncOperation.TRACKING,
                failedCount = failedSubjectIds.size,
                detail = "${failedSubjectIds.size} 個條目同步失敗",
            )
        }
        logger.info {
            "BangumiSync finish pullRequests=${fetched.requestCount} " +
                "pushRequests=$bangumiUpdated deleteRequests=$deletedRemote " +
                "localUpdates=$localUpdated success=$complete failed=${failedSubjectIds.size} " +
                "totalElapsedMs=${currentTimeMillis() - startedAt}"
        }
        return BangumiTrackingSyncResult(
            localUpdated = localUpdated,
            bangumiUpdated = bangumiUpdated,
            unchanged = unchanged,
            conflictsResolved = conflictsResolved,
            deletedRemote = deletedRemote,
            fetchedCollectionCount = fetched.items.size,
            expectedCollectionCount = fetched.total,
            elapsedMillis = currentTimeMillis() - startedAt,
            failedSubjectIds = failedSubjectIds.sorted(),
            failureMessages = failureMessages.distinct(),
        )
    }

    private suspend fun fetchAllCollections(username: String): TrackingCollectionFetch {
        val limit = 100
        val seenSubjectIds = HashSet<Int>()
        val first = timedRemoteMutation("GET", "/v0/users/{username}/collections") {
            bangumiApi.animeCollections(username, limit, 0)
        }
        val expectedTotal = first.total
        var requestCount = 1
        val result = ArrayList<BangumiTrackingRemoteSnapshot>(expectedTotal ?: first.collections.size)
        if (expectedTotal != null && first.collections.size > expectedTotal) {
            throw RepositoryRequestError("Bangumi 收藏第一頁超過 total=$expectedTotal")
        }
        if (expectedTotal != null && expectedTotal > first.collections.size && first.collections.size < limit) {
            throw RepositoryRequestError(
                "Bangumi 收藏第一頁提前結束：取得 ${first.collections.size}/$expectedTotal",
            )
        }
        first.collections.forEach { collection ->
            if (!seenSubjectIds.add(collection.subjectId)) {
                throw RepositoryRequestError("Bangumi 收藏第一頁含有重複 subject=${collection.subjectId}")
            }
        }
        result += first.collections
        syncCoordinator.report(
            operation = BangumiSyncOperation.TRACKING,
            phase = BangumiSyncPhase.FETCHING_COLLECTIONS,
            current = result.size,
            total = expectedTotal,
        )
        if (expectedTotal != null && expectedTotal > 0 && first.collections.isEmpty()) {
            throw RepositoryRequestError("Bangumi 收藏第一頁為空，但 total=$expectedTotal")
        }
        if (expectedTotal != null) {
            val offsets = (limit until expectedTotal step limit).toList()
            val pages = coroutineScope {
                offsets.map { offset ->
                    async {
                        offset to timedRemoteMutation("GET", "/v0/users/{username}/collections") {
                            bangumiApi.animeCollections(username, limit, offset)
                        }
                    }
                }.awaitAll()
            }.sortedBy { it.first }
            requestCount += pages.size
            pages.forEach { (offset, page) ->
                    if (page.collections.isEmpty() && offset < expectedTotal) {
                        throw RepositoryRequestError("Bangumi 收藏缺少 offset=$offset 的資料")
                    }
                    if (page.collections.size < limit && offset + page.collections.size < expectedTotal) {
                        throw RepositoryRequestError(
                            "Bangumi 收藏分頁提前結束：offset=$offset，取得 ${offset + page.collections.size}/$expectedTotal",
                        )
                    }
                    page.collections.forEach { collection ->
                        if (!seenSubjectIds.add(collection.subjectId)) {
                            throw RepositoryRequestError(
                                "Bangumi 收藏頁含有重複 subject=${collection.subjectId}，offset=$offset",
                            )
                        }
                    }
                    result += page.collections
                    syncCoordinator.report(
                        operation = BangumiSyncOperation.TRACKING,
                        phase = BangumiSyncPhase.FETCHING_COLLECTIONS,
                        current = result.size.coerceAtMost(expectedTotal),
                        total = expectedTotal,
                    )
            }
            val distinctCount = result.distinctBy { it.subjectId }.size
            if (result.size < expectedTotal || distinctCount < expectedTotal) {
                throw RepositoryRequestError(
                    "Bangumi 收藏資料不完整：取得 ${result.size}/$expectedTotal，去重後 $distinctCount",
                )
            }
        } else {
            var offset = first.collections.size
            while (first.collections.size >= limit) {
                val page = timedRemoteMutation("GET", "/v0/users/{username}/collections") {
                    bangumiApi.animeCollections(username, limit, offset)
                }
                requestCount++
                if (page.collections.isEmpty()) break
                page.collections.forEach { collection ->
                    if (!seenSubjectIds.add(collection.subjectId)) {
                        throw RepositoryRequestError(
                            "Bangumi 收藏頁含有重複 subject=${collection.subjectId}，offset=$offset",
                        )
                    }
                }
                result += page.collections
                offset += page.collections.size
                syncCoordinator.report(
                    operation = BangumiSyncOperation.TRACKING,
                    phase = BangumiSyncPhase.FETCHING_COLLECTIONS,
                    current = result.size,
                    total = null,
                )
                if (page.collections.size < limit) break
            }
        }
        return TrackingCollectionFetch(result.distinctBy { it.subjectId }, expectedTotal, requestCount)
    }

    private suspend fun upsertRemoteType(subjectId: Int, type: UnifiedCollectionType) {
        bangumiApi.upsertCollectionType(subjectId, type)
    }

    private suspend fun <T> timedRemoteMutation(
        method: String,
        endpoint: String,
        block: suspend () -> T,
    ): T {
        val startedAt = currentTimeMillis()
        return try {
            block().also {
                logger.info {
                    "Bangumi network method=$method endpoint=$endpoint status=success " +
                        "elapsedMs=${currentTimeMillis() - startedAt}"
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val status = (e as? ClientRequestException)?.response?.status?.value?.toString() ?: "error"
            logger.warn(e) {
                "Bangumi network method=$method endpoint=$endpoint status=$status " +
                    "elapsedMs=${currentTimeMillis() - startedAt}"
            }
            throw e
        }
    }

    private suspend fun executeRemoteMutation(
        username: String,
        accountKey: String,
        plan: BangumiTrackingSyncPlan,
    ): MutationResult {
        return when (val action = plan.action) {
            is BangumiTrackingSyncAction.UpsertRemote -> {
                timedRemoteMutation("POST", "/v0/users/{username}/collections/{subjectId}") {
                    upsertRemoteType(plan.subjectId, action.type)
                }
                val verified = readRemoteCollection(username, plan.subjectId)
                check(verified?.type == action.type) {
                    "Bangumi collection verification mismatch after UPSERT_COLLECTION"
                }
                metadataRepository.markMutationSucceeded(
                    subjectId = plan.subjectId,
                    remoteType = verified.type,
                    remoteUpdatedAt = verified.updatedAt,
                )
                MutationResult.UpsertSuccess(plan.subjectId)
            }

            BangumiTrackingSyncAction.DeleteRemote -> {
                ensureRemovalAccount(accountKey, username)
                if (readRemoteCollection(username, plan.subjectId) != null) {
                    timedRemoteMutation("POST", "/api/v1/collections/{subjectId}/removal/confirm") {
                        subjectService.deleteSubjectCollection(plan.subjectId)
                    }
                }
                check(readRemoteCollection(username, plan.subjectId) == null) {
                    "Bangumi collection still exists after DELETE_COLLECTION"
                }
                ensureRemovalAccount(accountKey, username)
                markVerifiedLocalRemoval(plan.subjectId)
                metadataRepository.markMutationSucceeded(
                    subjectId = plan.subjectId,
                    remoteType = null,
                    remoteUpdatedAt = null,
                    expectedAccountKey = accountKey,
                )
                MutationResult.DeleteSuccess(plan.subjectId)
            }

            else -> error("Not a remote mutation: ${plan.action}")
        }
    }

    private suspend fun ensureRemovalAccount(accountKey: String, username: String) {
        check(metadataRepository.accountKey() == accountKey) { "Account changed during collection removal" }
        val current = currentBangumiUser()
        check(current?.key == accountKey && current.username == username) {
            "Account changed during collection removal"
        }
    }

    private suspend fun markVerifiedLocalRemoval(subjectId: Int) {
        val now = currentTimeMillis()
        subjectCollectionDao.updateType(subjectId, UnifiedCollectionType.NOT_COLLECTED, now, now)
    }

    private suspend fun applyRemoteType(subjectId: Int, type: UnifiedCollectionType, remoteUpdatedAt: Long) {
        val updated = subjectCollectionDao.updateType(
            subjectId = subjectId,
            collectionType = type,
            lastUpdated = remoteUpdatedAt,
            lastFetched = currentTimeMillis(),
        )
        if (updated == 0) {
            val hydrated = subjectService.getSubjectCollection(subjectId)
            if (hydrated != null) {
                subjectCollectionDao.upsert(hydrated.toEntity(currentTimeMillis()).copy(lastUpdated = remoteUpdatedAt))
            }
        }
        metadataRepository.markRemoteObserved(
            subjectId = subjectId,
            remoteUpdatedAt = remoteUpdatedAt,
            remoteType = type,
            clearPending = true,
        )
    }

    private suspend fun readRemoteCollection(
        username: String,
        subjectId: Int,
    ): BangumiTrackingRemoteSnapshot? = timedRemoteMutation(
        method = "GET",
        endpoint = "/v0/users/{username}/collections/{subjectId}",
    ) {
        bangumiApi.collection(username, subjectId)
    }

    private suspend fun currentBangumiUser(): BangumiTrackingAccount? = bangumiApi.currentUser()

    private fun Throwable.toConnectionError(): BangumiTrackingConnectionError {
        return classifyBangumiTrackingError(this)
    }

    private sealed interface MutationResult {
        val subjectId: Int

        data class UpsertSuccess(override val subjectId: Int) : MutationResult
        data class DeleteSuccess(override val subjectId: Int) : MutationResult
        data class Failure(override val subjectId: Int, val error: Throwable) : MutationResult
    }

    private data class TrackingCollectionFetch(
        val items: List<BangumiTrackingRemoteSnapshot>,
        val total: Int?,
        val requestCount: Int,
    )

    private companion object {
        private val logger = logger<BangumiTrackingSyncRepository>()
    }
}
