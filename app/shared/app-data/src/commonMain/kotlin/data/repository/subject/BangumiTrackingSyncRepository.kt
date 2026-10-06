/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.him188.ani.app.data.persistent.database.dao.BangumiTrackingAccountEntity
import me.him188.ani.app.data.persistent.database.dao.BangumiTrackingMetadataDao
import me.him188.ani.app.data.persistent.database.dao.BangumiTrackingMetadataEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryException.Companion.wrapOrThrowCancellation
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Instant

data class BangumiTrackingAccount(
    val id: Int,
    val username: String,
) {
    val key: String get() = "id:$id"
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
                pendingError = null,
            ),
        )
    }

    suspend fun markRemoteObserved(subjectId: Int, remoteUpdatedAt: Long, syncedAt: Long = currentTimeMillis()) {
        val key = accountKey()
        val old = dao.find(key, subjectId)
        dao.upsert(
            (old ?: BangumiTrackingMetadataEntity(accountKey = key, subjectId = subjectId)).copy(
                remoteUpdatedAt = remoteUpdatedAt,
                lastSyncedAt = syncedAt,
            ),
        )
    }

    suspend fun clearTombstone(subjectId: Int, remoteUpdatedAt: Long? = null) {
        val key = accountKey()
        val old = dao.find(key, subjectId) ?: return
        dao.upsert(old.copy(localDeletedAt = null, remoteUpdatedAt = remoteUpdatedAt ?: old.remoteUpdatedAt))
    }

    suspend fun markPendingError(subjectId: Int, error: Throwable) {
        val key = accountKey()
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
    val remoteDeleteUnsupported: Int,
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

    data class RemoteDeleteUnsupported(
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
        is me.him188.ani.app.data.repository.RepositoryNetworkException ->
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
) : BangumiTrackingSyncEnqueuer {
    private val syncLock = Mutex()
    private var runningSync: kotlinx.coroutines.Deferred<BangumiTrackingSyncResult>? = null
    private val pendingJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    private val pendingLock = Mutex()
    private val _autoSyncEvents = MutableSharedFlow<BangumiTrackingAutoSyncEvent>(extraBufferCapacity = 16)
    val autoSyncEvents = _autoSyncEvents.asSharedFlow()

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
                    delay(AUTO_SYNC_DEBOUNCE_MILLIS)
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

    /**
     * Re-queues only mutations that were left pending by an earlier automatic sync. This is a
     * bounded retry trigger for app startup or a user-initiated connection check; it never fetches
     * the full collection list.
     */
    suspend fun retryPendingChanges(account: BangumiTrackingAccount) {
        if (!settingsStore.flow.first().autoSyncTracking) return
        val accountKey = metadataRepository.adoptAccount(account)
        metadataDao.list(accountKey)
            .asSequence()
            .filter { it.pendingType != null }
            .map { it.subjectId }
            .forEach(::enqueueLocalChange)
    }

    suspend fun syncNow(): BangumiTrackingSyncResult {
        val deferred = syncLock.withLock {
            runningSync ?: serviceScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                performFullSync()
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

    private suspend fun flushPending(subjectId: Int) {
        var attemptedType: UnifiedCollectionType? = null
        var accountKey: String? = null
        try {
            val settings = settingsStore.flow.first()
            if (!settings.autoSyncTracking) return
            val user = currentBangumiUser() ?: return
            val resolvedAccountKey = metadataRepository.adoptAccount(user)
            accountKey = resolvedAccountKey
            val metadata = metadataDao.find(resolvedAccountKey, subjectId) ?: return
            if (metadata.localDeletedAt != null) {
                _autoSyncEvents.tryEmit(BangumiTrackingAutoSyncEvent.RemoteDeleteUnsupported(subjectId))
                return
            }
            val type = metadata.pendingType
                ?.let { runCatching { UnifiedCollectionType.valueOf(it) }.getOrNull() }
                ?.takeUnless { it == UnifiedCollectionType.NOT_COLLECTED }
                ?: return
            attemptedType = type
            upsertRemoteType(subjectId, type)
            val latest = metadataDao.find(resolvedAccountKey, subjectId)
            if (latest?.localDeletedAt == null && latest?.pendingType == type.name) {
                metadataDao.upsert(
                    latest.copy(
                        pendingType = null,
                        pendingError = null,
                        lastSyncedAt = currentTimeMillis(),
                    ),
                )
                val previousSummary = metadataRepository.accountSummary(resolvedAccountKey)
                metadataRepository.saveAccountSummary(
                    accountKey = resolvedAccountKey,
                    lastSuccessfulSyncAt = currentTimeMillis(),
                    localCount = previousSummary?.localCount
                        ?: subjectCollectionDao.listAll().count { it.collectionType != UnifiedCollectionType.NOT_COLLECTED },
                    remoteCount = previousSummary?.remoteCount ?: 0,
                )
                _autoSyncEvents.tryEmit(BangumiTrackingAutoSyncEvent.Succeeded(subjectId, type))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val latest = accountKey?.let { metadataDao.find(it, subjectId) }
            val isCurrentAttempt = attemptedType == null || latest?.pendingType == attemptedType.name
            if (isCurrentAttempt) {
                metadataRepository.markPendingError(subjectId, e)
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
        val remote = fetchAllCollections(user.username).associateBy { it.subjectId }
        val subjectIds = (local.keys + remote.keys + metadata.keys).toSortedSet()
        var localUpdated = 0
        var bangumiUpdated = 0
        var unchanged = 0
        var conflicts = 0
        var remoteDeleteUnsupported = 0
        val plans = subjectIds.map { subjectId ->
            val tombstone = metadata[subjectId]?.localDeletedAt?.let(::BangumiTrackingTombstoneSnapshot)
            BangumiTrackingSyncEngine.plan(
                policy = settings.conflictPolicy,
                local = local[subjectId],
                remote = remote[subjectId],
                tombstone = tombstone,
            )
        }
        conflicts = plans.count { it.conflict }
        remoteDeleteUnsupported = plans.count { it.remoteDeleteUnsupported }

        val upserts = plans.mapNotNull { plan ->
            (plan.action as? BangumiTrackingSyncAction.UpsertRemote)?.let { plan.subjectId to it.type }
        }
        val semaphore = Semaphore(MAX_PARALLEL_MUTATIONS)
        val successfulMutations = coroutineScope {
            upserts.map { (subjectId, type) ->
                async {
                    semaphore.withPermit {
                        try {
                            upsertRemoteType(subjectId, type)
                            metadataDao.find(accountKey, subjectId)?.let { metadataRow ->
                                metadataDao.upsert(
                                    metadataRow.copy(
                                        pendingType = null,
                                        pendingError = null,
                                        lastSyncedAt = currentTimeMillis(),
                                        remoteUpdatedAt = currentTimeMillis(),
                                    ),
                                )
                            }
                            true
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            metadataRepository.markPendingError(subjectId, e)
                            throw e
                        }
                    }
                }
            }.awaitAll()
        }
        bangumiUpdated = successfulMutations.count { it }

        for (plan in plans) {
            when (val action = plan.action) {
                BangumiTrackingSyncAction.NoOp -> {
                    unchanged++
                    remote[plan.subjectId]?.let { metadataRepository.markRemoteObserved(plan.subjectId, it.updatedAt) }
                }

                is BangumiTrackingSyncAction.ApplyRemote -> {
                    val remoteSubject = remote[plan.subjectId] ?: continue
                    applyRemoteType(plan.subjectId, action.type, remoteSubject.updatedAt)
                    localUpdated++
                }

                BangumiTrackingSyncAction.KeepLocalUntracked -> {
                    val remoteSubject = remote[plan.subjectId]
                    if (remoteSubject != null) {
                        metadataRepository.markRemoteObserved(plan.subjectId, remoteSubject.updatedAt)
                    }
                    localUpdated++
                }

                BangumiTrackingSyncAction.MarkLocalUntracked -> {
                    val deletedAt = currentTimeMillis()
                    subjectCollectionDao.updateType(
                        subjectId = plan.subjectId,
                        collectionType = UnifiedCollectionType.NOT_COLLECTED,
                        lastUpdated = deletedAt,
                        lastFetched = deletedAt,
                    )
                    metadataRepository.markLocalDeletion(plan.subjectId, deletedAt)
                    localUpdated++
                }

                is BangumiTrackingSyncAction.UpsertRemote -> {
                    if (remote[plan.subjectId]?.type == action.type) unchanged++
                }
            }
        }
        val successfulAt = currentTimeMillis()
        metadataRepository.saveAccountSummary(
            accountKey = accountKey,
            lastSuccessfulSyncAt = successfulAt,
            localCount = subjectCollectionDao.listAll().count {
                it.collectionType != UnifiedCollectionType.NOT_COLLECTED
            },
            remoteCount = remote.size,
        )
        return BangumiTrackingSyncResult(
            localUpdated = localUpdated,
            bangumiUpdated = bangumiUpdated,
            unchanged = unchanged,
            conflictsResolved = conflicts,
            remoteDeleteUnsupported = remoteDeleteUnsupported,
        )
    }

    private suspend fun fetchAllCollections(username: String): List<BangumiTrackingRemoteSnapshot> {
        val result = ArrayList<BangumiTrackingRemoteSnapshot>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = bangumiApi.animeCollections(username, limit, offset)
            val data = page.collections
            result += data
            if (data.isEmpty() || data.size < limit || result.size >= (page.total ?: result.size)) break
            offset += data.size
        }
        return result.distinctBy { it.subjectId }
    }

    private suspend fun upsertRemoteType(subjectId: Int, type: UnifiedCollectionType) {
        bangumiApi.upsertCollectionType(subjectId, type)
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
        metadataRepository.clearTombstone(subjectId, remoteUpdatedAt)
        metadataRepository.markRemoteObserved(subjectId, remoteUpdatedAt)
    }

    private suspend fun currentBangumiUser(): BangumiTrackingAccount? = bangumiApi.currentUser()

    private fun Throwable.toConnectionError(): BangumiTrackingConnectionError {
        return classifyBangumiTrackingError(this)
    }

    private companion object {
        const val MAX_PARALLEL_MUTATIONS = 4
        const val AUTO_SYNC_DEBOUNCE_MILLIS = 500L
    }
}
