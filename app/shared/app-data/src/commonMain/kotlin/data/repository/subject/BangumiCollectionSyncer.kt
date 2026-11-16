package com.wynime.app.data.repository.subject

import androidx.datastore.core.DataStore
import com.wynime.app.data.network.WynimeCloudClient
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionDao
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.repository.episode.EpisodeCollectionPendingChange
import com.wynime.app.data.repository.episode.EpisodeCollectionPendingChanges
import com.wynime.app.data.repository.episode.EpisodeCollectionSyncEnqueuer
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.cloud.models.CollectionSyncMutation
import com.wynime.cloud.models.CollectionSyncRecord
import com.wynime.cloud.models.CollectionSyncRequest
import com.wynime.cloud.models.CollectionSyncResponse
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Serializable
enum class BangumiCollectionPendingKind {
    subject,
    episode,
}

@Serializable
data class BangumiCollectionPendingChange(
    val mutationId: String,
    val kind: BangumiCollectionPendingKind,
    val subjectId: Int,
    val episodeId: Int? = null,
    val modifiedAt: Long,
    val fieldModifiedAt: Map<String, Long> = emptyMap(),
    val changedFields: List<String>,
    val collectionType: String? = null,
    val rate: Int? = null,
    val comment: String? = null,
    val tags: List<String>? = null,
    val `private`: Boolean? = null,
    val episodeStatus: Int? = null,
    val volumeStatus: Int? = null,
)

@Serializable
data class BangumiCollectionSyncState(
    val accountKey: String? = null,
    val cursor: Long = 0,
    val changes: List<BangumiCollectionPendingChange> = emptyList(),
    val fieldModifiedAt: Map<String, Long> = emptyMap(),
    val bootstrapCompleted: Boolean = false,
    val quarantinedChanges: List<BangumiCollectionPendingChange> = emptyList(),
)

enum class BangumiCollectionUpstreamStatus {
    NOT_REQUESTED,
    PENDING,
    UNAVAILABLE,
    SYNCED,
}

data class BangumiCollectionSyncResult(
    val cloudSynced: Boolean,
    val upstreamStatus: BangumiCollectionUpstreamStatus,
    val pendingCount: Int,
    val appliedRecordCount: Int = 0,
    val bootstrapped: Boolean = false,
    val error: Throwable? = null,
) {
    val completed: Boolean
        get() = cloudSynced && upstreamStatus == BangumiCollectionUpstreamStatus.SYNCED && pendingCount == 0
}

data class BangumiSubjectCollectionChange(
    val subjectId: Int,
    val modifiedAt: Long,
    val fieldModifiedAt: Map<String, Long> = emptyMap(),
    val changedFields: List<String>,
    val collectionType: String? = null,
    val rate: Int? = null,
    val comment: String? = null,
    val tags: List<String>? = null,
    val `private`: Boolean? = null,
    val episodeStatus: Int? = null,
    val volumeStatus: Int? = null,
)

interface BangumiCollectionSyncEnqueuer {
    suspend fun enqueueSubject(change: BangumiSubjectCollectionChange)
}

interface BangumiCollectionSyncCoordinator {
    suspend fun prepareForCurrentAccount()
    suspend fun syncNow(reconcile: Boolean = true): BangumiCollectionSyncResult
}

class BangumiCollectionSyncer(
    private val pendingStore: DataStore<BangumiCollectionSyncState>,
    private val cloudClient: WynimeCloudClient,
    private val tokenRepository: TokenRepository,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val deviceIdProvider: suspend () -> String,
    private val scope: CoroutineScope,
    private val legacyEpisodePendingStore: DataStore<EpisodeCollectionPendingChanges>? = null,
    private val accountKeyProvider: suspend () -> String? = { null },
) : BangumiCollectionSyncEnqueuer,
    EpisodeCollectionSyncEnqueuer,
    BangumiCollectionSyncCoordinator {

    private val syncMutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var requestJob: Job? = null
    private var cloudApiVersion: Int? = null
    private var cloudHealthCheckedAt: Long = 0L

    fun start() {
        if (requestJob != null) return
        scope.launch(CoroutineName("BangumiCollectionSyncer.migratePending")) {
            migrateLegacyEpisodeChanges()
        }
        requestJob = scope.launch(CoroutineName("BangumiCollectionSyncer.requestGate")) {
            while (currentCoroutineContext().isActive) {
                requests.receive()
                do {
                    syncOnceCatching(fullSnapshot = false, reconcile = false)
                } while (requests.tryReceive().isSuccess)
            }
        }
        scope.launch(CoroutineName("BangumiCollectionSyncer.session")) {
            tokenRepository.refreshToken.collectLatest { token ->
                if (!token.isNullOrBlank()) requestSync()
                if (token.isNullOrBlank()) return@collectLatest
                while (currentCoroutineContext().isActive) {
                    delay(RECONCILE_INTERVAL_MILLIS)
                    syncOnceCatching(fullSnapshot = false, reconcile = true)
                }
            }
        }
    }

    override suspend fun enqueueSubject(change: BangumiSubjectCollectionChange) {
        require(change.subjectId > 0) { "subjectId must be positive" }
        require(change.changedFields.isNotEmpty()) { "changedFields must not be empty" }
        syncMutex.withLock {
            addChange(
                BangumiCollectionPendingChange(
                    mutationId = newMutationId(),
                    kind = BangumiCollectionPendingKind.subject,
                    subjectId = change.subjectId,
                    modifiedAt = change.modifiedAt,
                    fieldModifiedAt = change.changedFields.associateWith {
                        change.fieldModifiedAt[it] ?: change.modifiedAt
                    },
                    changedFields = change.changedFields,
                    collectionType = change.collectionType,
                    rate = change.rate,
                    comment = change.comment,
                    tags = change.tags,
                    `private` = change.`private`,
                    episodeStatus = change.episodeStatus,
                    volumeStatus = change.volumeStatus,
                ),
            )
        }
    }

    override suspend fun enqueue(changes: Collection<EpisodeCollectionPendingChange>) {
        if (changes.isEmpty()) return
        syncMutex.withLock {
            val pending = changes.map { change ->
                BangumiCollectionPendingChange(
                    mutationId = newMutationId(),
                    kind = BangumiCollectionPendingKind.episode,
                    subjectId = change.subjectId,
                    episodeId = change.episodeId,
                    modifiedAt = change.modifiedAt,
                    fieldModifiedAt = mapOf("collectionType" to change.modifiedAt),
                    changedFields = listOf("collectionType"),
                    collectionType = change.collectionType.name,
                )
            }
            mergePendingChanges(pending)
        }
    }

    override suspend fun syncNow(reconcile: Boolean): BangumiCollectionSyncResult {
        val token = tokenRepository.refreshToken.first()?.takeIf(String::isNotBlank)
            ?: return pendingStore.data.first().let { state ->
                BangumiCollectionSyncResult(
                    cloudSynced = false,
                    upstreamStatus = BangumiCollectionUpstreamStatus.UNAVAILABLE,
                    pendingCount = state.changes.size,
                    error = IllegalStateException("Cloud session is not available"),
                )
            }
        return syncMutex.withLock {
            try {
                syncOnce(token, fullSnapshot = true, reconcile = reconcile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val pendingCount = pendingStore.data.first().changes.size
                BangumiCollectionSyncResult(
                    cloudSynced = false,
                    upstreamStatus = BangumiCollectionUpstreamStatus.UNAVAILABLE,
                    pendingCount = pendingCount,
                    error = e,
                )
            }
        }
    }

    fun requestSync() {
        requests.trySend(Unit)
    }

    override suspend fun prepareForCurrentAccount() {
        val token = tokenRepository.refreshToken.first()?.takeIf(String::isNotBlank) ?: return
        val accountKey = accountKey(token)
        syncMutex.withLock {
            scopeState(accountKey, resetLocalState = true)
        }
    }

    private suspend fun migrateLegacyEpisodeChanges() {
        val legacyStore = legacyEpisodePendingStore ?: return
        val legacy = legacyStore.data.first()
        if (legacy.changes.isEmpty()) return
        val migrated = legacy.changes.map { change ->
            BangumiCollectionPendingChange(
                mutationId = newMutationId(),
                kind = BangumiCollectionPendingKind.episode,
                subjectId = change.subjectId,
                episodeId = change.episodeId,
                modifiedAt = change.modifiedAt,
                fieldModifiedAt = mapOf("collectionType" to change.modifiedAt),
                changedFields = listOf("collectionType"),
                collectionType = change.collectionType.name,
            )
        }
        syncMutex.withLock {
            mergePendingChanges(migrated)
        }
        legacyStore.updateData { EpisodeCollectionPendingChanges() }
    }

    private suspend fun addChange(change: BangumiCollectionPendingChange) {
        mergePendingChanges(listOf(change))
    }

    private suspend fun mergePendingChanges(changes: Collection<BangumiCollectionPendingChange>) {
        if (changes.isEmpty()) return
        pendingStore.updateData { state ->
            val latest = state.changes
                .associateBy { it.kind to (it.subjectId to it.episodeId) }
                .toMutableMap()
            val clocks = state.fieldModifiedAt.toMutableMap()
            changes.forEach { change ->
                val key = change.kind to (change.subjectId to change.episodeId)
                val previous = latest[key]
                if (previous == null || change.hasNewerFieldThan(previous)) {
                    latest[key] = previous?.merge(change) ?: change
                }
                change.changedFields.forEach { field ->
                    val modifiedAt = change.fieldModifiedAt[field] ?: change.modifiedAt
                    val clockKey = fieldClockKey(change.kind, change.subjectId, change.episodeId, field)
                    if (modifiedAt >= (clocks[clockKey] ?: Long.MIN_VALUE)) clocks[clockKey] = modifiedAt
                }
            }
            state.copy(
                changes = latest.values.sortedBy { it.modifiedAt },
                fieldModifiedAt = clocks,
            )
        }
        requestSync()
    }

    private suspend fun syncOnceCatching(fullSnapshot: Boolean, reconcile: Boolean) {
        try {
            val token = tokenRepository.refreshToken.first()?.takeIf(String::isNotBlank) ?: return
            syncMutex.withLock {
                syncOnce(token, fullSnapshot, reconcile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.info(e) { "Cloud collection sync failed; pending changes are retained" }
            scope.launch {
                delay(RETRY_DELAY_MILLIS)
                requestSync()
            }
        }
    }

    private suspend fun syncOnce(
        token: String,
        fullSnapshot: Boolean,
        reconcile: Boolean,
    ): BangumiCollectionSyncResult {
        ensureCloudApiAvailable()
        val accountKey = accountKey(token)
        scopeState(accountKey)
        var response = sendAndApply(token, fullSnapshot, reconcile)
        var bootstrapped = pendingStore.data.first().bootstrapCompleted

        if (fullSnapshot && !bootstrapped) {
            // Seed every locally known field on the first authenticated pull. This
            // must not depend on response.records being empty: a pending local
            // mutation can make an otherwise empty cloud store return one record.
            val localChanges = localSnapshotChanges()
            if (localChanges.isNotEmpty()) {
                mergePendingChanges(localChanges)
                response = sendAndApply(token, fullSnapshot = false, reconcile = reconcile)
            }
            bootstrapped = true
            pendingStore.updateData { current ->
                if (current.accountKey == accountKey) current.copy(bootstrapCompleted = true) else current
            }
        }

        return BangumiCollectionSyncResult(
            cloudSynced = true,
            upstreamStatus = response.upstreamStatus.toAppStatus(),
            pendingCount = response.pendingCount,
            appliedRecordCount = response.records.size,
            bootstrapped = bootstrapped,
        )
    }

    private suspend fun sendAndApply(
        token: String,
        fullSnapshot: Boolean,
        reconcile: Boolean,
    ): CollectionSyncResponse {
        val state = pendingStore.data.first()
        val deviceId = deviceIdProvider()
        val request = CollectionSyncRequest(
            deviceId = deviceId,
            cursor = state.cursor,
            mode = if (reconcile) CollectionSyncRequest.Mode.reconcile else CollectionSyncRequest.Mode.fast,
            changes = state.changes.take(MAX_CHANGES_PER_REQUEST).map { it.toCloudMutation(deviceId) },
            fullSnapshot = fullSnapshot,
            includeEpisodes = true,
        )
        val response = cloudClient.syncCollections(token, request)
        val clocks = applyRecords(response, state.changes, state.fieldModifiedAt)
        val accepted = response.accepted.mapTo(HashSet()) { it.mutationId }
        pendingStore.updateData { current ->
            current.copy(
                cursor = maxOf(current.cursor, response.cursor),
                changes = current.changes.filterNot { it.mutationId in accepted },
                fieldModifiedAt = clocks,
            )
        }
        if (pendingStore.data.first().changes.isNotEmpty()) requestSync()
        return response
    }

    private suspend fun ensureCloudApiAvailable() {
        val now = currentTimeMillis()
        if (cloudApiVersion != null && now - cloudHealthCheckedAt < HEALTH_CACHE_MILLIS) return
        val health = cloudClient.health()
        check(health.ok && health.apiVersion >= REQUIRED_CLOUD_API_VERSION) {
            "Unsupported Wynime Cloud collection API version: ${health.apiVersion}"
        }
        cloudApiVersion = health.apiVersion
        cloudHealthCheckedAt = now
    }

    private suspend fun accountKey(token: String): String = accountKeyProvider()?.takeIf(String::isNotBlank)
        ?: "token:${token.hashCode().toUInt().toString(16)}"

    private suspend fun scopeState(accountKey: String, resetLocalState: Boolean = false) {
        var accountChanged = false
        pendingStore.updateData { state ->
            if (state.accountKey == accountKey) return@updateData state
            val isProvisionalAccountUpgrade = state.accountKey?.startsWith("token:") == true &&
                accountKey.startsWith("id:")
            if (isProvisionalAccountUpgrade) {
                // The token hash is used before Bangumi's user identity has been
                // adopted. Promote it to the stable account key without throwing
                // away the cursor or pending mutations collected in the meantime.
                return@updateData state.copy(accountKey = accountKey)
            }
            accountChanged = state.accountKey != null || state.cursor != 0L || state.changes.isNotEmpty()
            if (state.accountKey == null && state.cursor == 0L && state.changes.isEmpty()) {
                return@updateData state.copy(accountKey = accountKey)
            }
            state.copy(
                accountKey = accountKey,
                cursor = 0,
                changes = emptyList(),
                fieldModifiedAt = emptyMap(),
                bootstrapCompleted = false,
                quarantinedChanges = (state.quarantinedChanges + state.changes).takeLast(MAX_QUARANTINED_CHANGES),
            )
        }
        if (resetLocalState && accountChanged) clearLocalCollectionState()
    }

    private suspend fun clearLocalCollectionState() {
        val subjects = subjectCollectionDao.listAll()
        if (subjects.isNotEmpty()) {
            subjectCollectionDao.upsert(subjects.map { entity ->
                entity.copy(
                    selfRatingInfo = SelfRatingInfo.Empty,
                    collectionType = UnifiedCollectionType.NOT_COLLECTED,
                    lastUpdated = 0L,
                )
            })
        }
        val episodes = episodeCollectionDao.all().first()
        if (episodes.isNotEmpty()) {
            episodeCollectionDao.upsert(episodes.map { it.copy(selfCollectionType = UnifiedCollectionType.NOT_COLLECTED) })
        }
    }

    private suspend fun localSnapshotChanges(): List<BangumiCollectionPendingChange> {
        val fallbackModifiedAt = currentTimeMillis()
        val subjectChanges = subjectCollectionDao.listAll()
            .asSequence()
            .filter { it.collectionType != UnifiedCollectionType.NOT_COLLECTED }
            .map { entity ->
                val modifiedAt = entity.lastUpdated.takeIf { it > 0 } ?: fallbackModifiedAt
                BangumiCollectionPendingChange(
                    mutationId = newMutationId(),
                    kind = BangumiCollectionPendingKind.subject,
                    subjectId = entity.subjectId,
                    modifiedAt = modifiedAt,
                    fieldModifiedAt = SNAPSHOT_SUBJECT_FIELDS.associateWith { modifiedAt },
                    changedFields = SNAPSHOT_SUBJECT_FIELDS,
                    collectionType = entity.collectionType.name,
                    rate = entity.selfRatingInfo.score,
                    comment = entity.selfRatingInfo.comment,
                    tags = entity.selfRatingInfo.tags,
                    `private` = entity.selfRatingInfo.isPrivate,
                )
            }
        val episodeChanges = episodeCollectionDao.all().first()
            .asSequence()
            .filter { it.selfCollectionType != UnifiedCollectionType.NOT_COLLECTED }
            .map { entity ->
                val modifiedAt = fallbackModifiedAt
                BangumiCollectionPendingChange(
                    mutationId = newMutationId(),
                    kind = BangumiCollectionPendingKind.episode,
                    subjectId = entity.subjectId,
                    episodeId = entity.episodeId,
                    modifiedAt = modifiedAt,
                    fieldModifiedAt = mapOf("collectionType" to modifiedAt),
                    changedFields = listOf("collectionType"),
                    collectionType = entity.selfCollectionType.name,
                )
            }
        return (subjectChanges + episodeChanges).toList()
    }

    private suspend fun applyRecords(
        response: CollectionSyncResponse,
        pending: List<BangumiCollectionPendingChange>,
        fieldClocks: Map<String, Long>,
    ): Map<String, Long> {
        var nextClocks = fieldClocks
        response.records.forEach { record ->
            when (record.kind) {
                CollectionSyncRecord.Kind.subject ->
                    applySubjectRecord(record, pending, fieldClocks)

                CollectionSyncRecord.Kind.episode ->
                    applyEpisodeRecord(record, pending, fieldClocks)
            }
            record.fieldModifiedAt.forEach { (field, modifiedAt) ->
                val key = fieldClockKey(record, field)
                if (modifiedAt >= (nextClocks[key] ?: Long.MIN_VALUE)) {
                    nextClocks = nextClocks + (key to modifiedAt)
                }
            }
        }
        return nextClocks
    }

    private suspend fun applySubjectRecord(
        record: CollectionSyncRecord,
        pending: List<BangumiCollectionPendingChange>,
        fieldClocks: Map<String, Long>,
    ) {
        val entity = subjectCollectionDao.findById(record.subjectId).firstOrNull() ?: return
        val keyPending = pending.filter {
            it.kind == BangumiCollectionPendingKind.subject && it.subjectId == record.subjectId
        }
        fun mayApply(field: String): Boolean {
            val remoteTime = record.fieldModifiedAt[field] ?: return false
            val localTime = keyPending
                .filter { field in it.changedFields }
                .maxOfOrNull { it.fieldModifiedAt[field] ?: it.modifiedAt }
            val clockTime = fieldClocks[fieldClockKey(record, field)]
            return remoteTime >= maxOf(localTime ?: Long.MIN_VALUE, clockTime ?: Long.MIN_VALUE)
        }

        var next = entity
        if (mayApply("collectionType") && "collectionType" in record.fieldModifiedAt) {
            next = next.copy(collectionType = record.collectionType.toCollectionType())
        }
        val currentRating = entity.selfRatingInfo
        next = next.copy(
            selfRatingInfo = currentRating.copy(
                score = if (mayApply("rate")) record.rate ?: 0 else currentRating.score,
                comment = if (mayApply("comment")) record.comment else currentRating.comment,
                tags = if (mayApply("tags")) record.tags.orEmpty() else currentRating.tags,
                isPrivate = if (mayApply("private")) record.`private` ?: false else currentRating.isPrivate,
            ),
            lastUpdated = maxOf(entity.lastUpdated, record.fieldModifiedAt.values.maxOrNull() ?: entity.lastUpdated),
        )
        if (next != entity) subjectCollectionDao.upsert(next)
    }

    private suspend fun applyEpisodeRecord(
        record: CollectionSyncRecord,
        pending: List<BangumiCollectionPendingChange>,
        fieldClocks: Map<String, Long>,
    ) {
        val episodeId = record.episodeId ?: return
        val remoteTime = record.fieldModifiedAt["collectionType"] ?: return
        val pendingTime = pending
            .filter {
                it.kind == BangumiCollectionPendingKind.episode &&
                    it.subjectId == record.subjectId && it.episodeId == episodeId
            }
            .maxOfOrNull { it.fieldModifiedAt["collectionType"] ?: it.modifiedAt }
        val localTime = maxOf(
            pendingTime ?: Long.MIN_VALUE,
            fieldClocks[fieldClockKey(record, "collectionType")] ?: Long.MIN_VALUE,
        )
        if (remoteTime < localTime) return
        val entity = episodeCollectionDao.findByEpisodeId(episodeId).firstOrNull() ?: return
        val type = record.collectionType.toCollectionType()
        if (entity.selfCollectionType != type) {
            episodeCollectionDao.updateSelfCollectionType(record.subjectId, episodeId, type)
        }
    }

    private fun BangumiCollectionPendingChange.toCloudMutation(deviceId: String) = CollectionSyncMutation(
        mutationId = mutationId,
        deviceId = deviceId,
        kind = when (kind) {
            BangumiCollectionPendingKind.subject -> CollectionSyncMutation.Kind.subject
            BangumiCollectionPendingKind.episode -> CollectionSyncMutation.Kind.episode
        },
        subjectId = subjectId,
        episodeId = episodeId,
        modifiedAt = modifiedAt,
        fieldModifiedAt = fieldModifiedAt.ifEmpty {
            changedFields.associateWith { modifiedAt }
        },
        changedFields = changedFields,
        collectionType = collectionType,
        rate = rate,
        comment = comment,
        tags = tags,
        `private` = this.`private`,
        episodeStatus = episodeStatus,
        volumeStatus = volumeStatus,
    )

    private fun String?.toCollectionType(): UnifiedCollectionType = runCatching {
        UnifiedCollectionType.valueOf(this ?: UnifiedCollectionType.NOT_COLLECTED.name)
    }.getOrDefault(UnifiedCollectionType.NOT_COLLECTED)

    @OptIn(ExperimentalUuidApi::class)
    private fun newMutationId(): String = Uuid.random().toString()

    private companion object {
        const val MAX_CHANGES_PER_REQUEST = 200
        const val RETRY_DELAY_MILLIS = 5_000L
        const val RECONCILE_INTERVAL_MILLIS = 60_000L
        val logger = logger<BangumiCollectionSyncer>()
    }
}

private fun BangumiCollectionPendingChange.merge(
    newer: BangumiCollectionPendingChange,
): BangumiCollectionPendingChange {
    require(kind == newer.kind && subjectId == newer.subjectId && episodeId == newer.episodeId)
    val fields = (changedFields + newer.changedFields).distinct()
    fun timestamp(change: BangumiCollectionPendingChange, field: String): Long =
        change.fieldModifiedAt[field] ?: change.modifiedAt
    fun useNewer(field: String): Boolean =
        field in newer.changedFields &&
            (field !in changedFields || timestamp(newer, field) >= timestamp(this, field))
    fun <T> mergedValue(field: String, old: T?, next: T?): T? =
        if (useNewer(field)) next else old
    val mergedFieldModifiedAt = fields.associateWith { field ->
        if (useNewer(field)) timestamp(newer, field) else timestamp(this, field)
    }
    return newer.copy(
        modifiedAt = mergedFieldModifiedAt.values.maxOrNull() ?: newer.modifiedAt,
        fieldModifiedAt = mergedFieldModifiedAt,
        changedFields = fields,
        collectionType = mergedValue("collectionType", collectionType, newer.collectionType),
        rate = mergedValue("rate", rate, newer.rate),
        comment = mergedValue("comment", comment, newer.comment),
        tags = mergedValue("tags", tags, newer.tags),
        `private` = mergedValue("private", `private`, newer.`private`),
        episodeStatus = mergedValue("episodeStatus", episodeStatus, newer.episodeStatus),
        volumeStatus = mergedValue("volumeStatus", volumeStatus, newer.volumeStatus),
    )
}

private fun BangumiCollectionPendingChange.hasNewerFieldThan(
    previous: BangumiCollectionPendingChange,
): Boolean = changedFields.any { field ->
    field !in previous.changedFields ||
        (fieldModifiedAt[field] ?: modifiedAt) >=
            (previous.fieldModifiedAt[field] ?: previous.modifiedAt)
}

private fun fieldClockKey(
    kind: BangumiCollectionPendingKind,
    subjectId: Int,
    episodeId: Int?,
    field: String,
): String = "$kind:$subjectId:${episodeId ?: 0}:$field"

private fun fieldClockKey(
    record: CollectionSyncRecord,
    field: String,
): String = fieldClockKey(
    kind = when (record.kind) {
        CollectionSyncRecord.Kind.subject -> BangumiCollectionPendingKind.subject
        CollectionSyncRecord.Kind.episode -> BangumiCollectionPendingKind.episode
    },
    subjectId = record.subjectId,
    episodeId = record.episodeId,
    field = field,
)

private fun CollectionSyncResponse.UpstreamStatus.toAppStatus(): BangumiCollectionUpstreamStatus = when (this) {
    CollectionSyncResponse.UpstreamStatus.not_requested -> BangumiCollectionUpstreamStatus.NOT_REQUESTED
    CollectionSyncResponse.UpstreamStatus.pending -> BangumiCollectionUpstreamStatus.PENDING
    CollectionSyncResponse.UpstreamStatus.unavailable -> BangumiCollectionUpstreamStatus.UNAVAILABLE
    CollectionSyncResponse.UpstreamStatus.synced -> BangumiCollectionUpstreamStatus.SYNCED
}

private const val REQUIRED_CLOUD_API_VERSION = 1
private const val HEALTH_CACHE_MILLIS = 60_000L
private const val MAX_QUARANTINED_CHANGES = 500
private val SNAPSHOT_SUBJECT_FIELDS = listOf("collectionType", "rate", "comment", "tags", "private")
