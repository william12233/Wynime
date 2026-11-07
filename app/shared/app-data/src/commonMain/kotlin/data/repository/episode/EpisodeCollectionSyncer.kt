package com.wynime.app.data.repository.episode

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.ServerResponseException
import com.wynime.app.data.network.EpisodeService
import com.wynime.app.domain.session.SessionEvent
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import kotlinx.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class EpisodeCollectionPendingChange(
    val subjectId: Int,
    val episodeId: Int,
    val collectionType: UnifiedCollectionType,
    val modifiedAt: Long,
)

@Serializable
data class EpisodeCollectionPendingChanges(
    val changes: List<EpisodeCollectionPendingChange> = emptyList(),
)

interface EpisodeCollectionSyncEnqueuer {
    suspend fun enqueue(changes: Collection<EpisodeCollectionPendingChange>)
}

class EpisodeCollectionSyncer(
    private val pendingStore: DataStore<EpisodeCollectionPendingChanges>,
    private val episodeService: EpisodeService,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val requestCooldown: Duration = 5.seconds,
) : EpisodeCollectionSyncEnqueuer {
    private val syncMutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        require(requestCooldown.isPositive()) { "requestCooldown must be positive" }
        scope.launch(CoroutineName("EpisodeCollectionSyncer.requestGate")) {
            while (currentCoroutineContext().isActive) {
                requests.receive()
                do {
                    syncOnceCatching()
                    delay(requestCooldown)
                } while (requests.tryReceive().isSuccess)
            }
        }
    }

    fun start() {
        scope.launch(CoroutineName("EpisodeCollectionSyncer.session")) {
            sessionStateProvider.stateFlow.collectLatest { state ->
                if (state !is SessionState.Valid || !state.bangumiConnected) return@collectLatest
                requestSync()
                while (currentCoroutineContext().isActive) {
                    delay(STEADY_SYNC_INTERVAL)
                    requestSync()
                }
            }
        }
        scope.launch(CoroutineName("EpisodeCollectionSyncer.login")) {
            sessionStateProvider.eventFlow.collect { event ->
                if (event is SessionEvent.NewLogin) requestSync()
            }
        }
    }

    override suspend fun enqueue(changes: Collection<EpisodeCollectionPendingChange>) {
        if (changes.isEmpty()) return
        pendingStore.updateData { current ->
            val latest = current.changes
                .associateBy { it.subjectId to it.episodeId }
                .toMutableMap()
            changes.forEach { change ->
                val key = change.subjectId to change.episodeId
                if ((latest[key]?.modifiedAt ?: Long.MIN_VALUE) <= change.modifiedAt) {
                    latest[key] = change
                }
            }
            EpisodeCollectionPendingChanges(latest.values.sortedBy { it.modifiedAt })
        }
        requestSync()
    }

    suspend fun syncOnce() = syncMutex.withLock {
        val state = sessionStateProvider.stateFlow.first()
        if (state !is SessionState.Valid || !state.bangumiConnected) return@withLock

        for (change in pendingStore.data.first().changes) {
            val updated = try {
                setEpisodeCollectionWithRetry(change)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.info(e) {
                    "Episode collection sync paused at subject=${change.subjectId} " +
                        "episode=${change.episodeId}"
                }
                return@withLock
            }

            if (!updated) {
                logger.info {
                    "Episode collection sync returned no update for " +
                        "subject=${change.subjectId} episode=${change.episodeId}; dropping pending change"
                }
            }
            remove(change)
        }
    }

    private suspend fun setEpisodeCollectionWithRetry(change: EpisodeCollectionPendingChange): Boolean {
        var lastError: Throwable? = null
        repeat(SYNC_ATTEMPTS) { attempt ->
            try {
                return episodeService.setEpisodeCollection(
                    subjectId = change.subjectId,
                    episodeId = listOf(change.episodeId),
                    type = change.collectionType,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e !is IOException && e !is ServerResponseException) throw e
                lastError = e
                if (attempt + 1 < SYNC_ATTEMPTS) {
                    delay(SYNC_RETRY_DELAYS[attempt])
                }
            }
        }
        throw checkNotNull(lastError)
    }

    fun requestSync() {
        requests.trySend(Unit)
    }

    private suspend fun syncOnceCatching() {
        try {
            syncOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.info(e) { "Episode collection sync failed; pending changes are retained" }
        }
    }

    private suspend fun remove(change: EpisodeCollectionPendingChange) {
        pendingStore.updateData { current ->
            current.copy(
                changes = current.changes.filterNot {
                    it.subjectId == change.subjectId &&
                        it.episodeId == change.episodeId &&
                        it.collectionType == change.collectionType &&
                        it.modifiedAt == change.modifiedAt
                },
            )
        }
    }

    private companion object {
        val STEADY_SYNC_INTERVAL = 60.seconds
        val SYNC_RETRY_DELAYS = longArrayOf(750L, 1_500L)
        const val SYNC_ATTEMPTS = 3
        val logger = logger<EpisodeCollectionSyncer>()
    }
}
