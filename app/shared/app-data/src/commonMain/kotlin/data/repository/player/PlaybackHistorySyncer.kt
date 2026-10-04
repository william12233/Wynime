/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.network.WynimeCloudClient
import me.him188.ani.app.data.network.WynimePlaybackChange
import me.him188.ani.app.data.network.WynimePlaybackServerChange
import me.him188.ani.app.data.network.WynimePlaybackSyncRequest
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 將本機播放紀錄以 local-first 方式同步到 Wynime Cloud。
 *
 * 播放器只寫入 Room 和 pending-op；網路錯誤不會回傳到播放流程。Worker 以 Bangumi user、subjectId、episodeId
 * 為複合身份，並透過 revision/baseRevision 在多裝置同時更新時做 deterministic conflict resolution。
 */
class PlaybackHistorySyncer(
    private val repository: EpisodePlayHistoryRepository,
    private val cloudClient: WynimeCloudClient,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    requestCooldown: Duration = 5.seconds,
) : Repository() {
    private val syncMutex = Mutex()
    private val requestGate = LeadingTrailingSyncGate(
        scope = scope,
        cooldown = requestCooldown,
        task = ::syncOnceCatching,
    )

    fun start() {
        scope.launch(CoroutineName("PlaybackHistorySyncer.session")) {
            sessionStateProvider.stateFlow.collectLatest { state ->
                if (state !is SessionState.Valid || !state.bangumiConnected) return@collectLatest
                requestSync()
                while (currentCoroutineContext().isActive) {
                    delay(STEADY_SYNC_INTERVAL)
                    requestSync()
                }
            }
        }
        scope.launch(CoroutineName("PlaybackHistorySyncer.login")) {
            sessionStateProvider.eventFlow.collect { event ->
                if (event is SessionEvent.NewLogin) requestSync()
            }
        }
    }

    fun requestSync() {
        requestGate.request()
    }

    suspend fun syncOnce() = syncMutex.withLock {
        val state = sessionStateProvider.stateFlow.first()
        if (state !is SessionState.Valid || !state.bangumiConnected) return@withLock

        val cloudSessionToken = tokenRepository.refreshToken.first()?.takeIf(String::isNotBlank)
            ?: return@withLock
        val pendingOps = repository.pendingOpsFlow.first()
        val changes = pendingOps.latestPerIdentity().mapNotNull { it.toCloudChange() }
        val sentPendingOpIds = pendingOps
            .filter { it.toCloudChange() != null }
            .map { it.id }
        val storedCursor = repository.lastSyncAtMillisFlow.first()
        val cursor = storedCursor.takeUnless { it >= LEGACY_TIMESTAMP_THRESHOLD } ?: 0L
        val deviceId = settingsRepository.analyticsSettings.flow.first().deviceId

        val response = withContext(ioDispatcher) {
            cloudClient.syncPlayback(
                sessionToken = cloudSessionToken,
                request = WynimePlaybackSyncRequest(
                    deviceId = deviceId,
                    cursor = cursor,
                    changes = changes,
                ),
            )
        }

        repository.applySyncResult(
            sentPendingOpIds = sentPendingOpIds,
            records = response.serverChanges.map { it.toEpisodeHistory() },
            nextSyncAtMillis = response.cursor,
        )
    }

    private suspend fun syncOnceCatching() {
        try {
            syncOnce()
        } catch (e: Exception) {
            RepositoryException.wrapOrThrowCancellation(e)
            logger.info { "Failed to sync playback histories: ${e.message}" }
        }
    }

    private fun PlaybackHistoryPendingOp.toCloudChange(): WynimePlaybackChange? {
        return when (this) {
            is PlaybackHistoryPendingOp.Upsert -> WynimePlaybackChange(
                subjectId = subjectId,
                episodeId = episodeId,
                positionMs = positionMillis,
                durationMs = durationMillis,
                completed = durationMillis > 0 && positionMillis >= durationMillis,
                lastPlayedAt = updatedAtMillis,
                baseRevision = baseRevision,
            )

            is PlaybackHistoryPendingOp.Delete -> {
                val subjectId = subjectId ?: return null
                WynimePlaybackChange(
                    subjectId = subjectId,
                    episodeId = episodeId,
                    positionMs = 0,
                    durationMs = 0,
                    completed = false,
                    lastPlayedAt = deletedAtMillis,
                    baseRevision = baseRevision,
                    deleted = true,
                )
            }
        }
    }

    private fun WynimePlaybackServerChange.toEpisodeHistory(): EpisodeHistory {
        return EpisodeHistory(
            episodeId = episodeId,
            positionMillis = positionMs,
            subjectId = subjectId,
            durationMillis = durationMs.takeIf { it > 0 },
            updatedAtMillis = lastPlayedAt,
            deletedAtMillis = lastPlayedAt.takeIf { deleted },
            serverRevision = revision,
            isDirty = false,
        )
    }

    private companion object {
        val STEADY_SYNC_INTERVAL = 60.seconds
        const val LEGACY_TIMESTAMP_THRESHOLD = 100_000_000_000L
    }
}

/**
 * Runs the first request immediately, then coalesces requests received during [cooldown] into one trailing run.
 * Every trailing run starts a new cooldown of its own.
 */
internal class LeadingTrailingSyncGate(
    scope: CoroutineScope,
    private val cooldown: Duration,
    private val task: suspend () -> Unit,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        require(cooldown.isPositive()) { "cooldown must be positive" }
        scope.launch(CoroutineName("PlaybackHistorySyncer.requestGate")) {
            while (currentCoroutineContext().isActive) {
                requests.receive()
                do {
                    task()
                    delay(cooldown)
                } while (requests.tryReceive().isSuccess)
            }
        }
    }

    fun request() {
        requests.trySend(Unit)
    }
}

internal fun List<PlaybackHistoryPendingOp>.latestPerIdentity(): List<PlaybackHistoryPendingOp> {
    return groupBy { it.subjectId to it.episodeId }
        .values
        .map { episodeOps ->
            episodeOps.maxWith(
                compareBy(PlaybackHistoryPendingOp::versionMillis, PlaybackHistoryPendingOp::id),
            )
        }
        .sortedBy(PlaybackHistoryPendingOp::id)
}

internal fun List<PlaybackHistoryPendingOp>.latestPerEpisode(): List<PlaybackHistoryPendingOp> {
    return groupBy(PlaybackHistoryPendingOp::episodeId)
        .values
        .map { episodeOps ->
            episodeOps.maxWith(
                compareBy(PlaybackHistoryPendingOp::versionMillis, PlaybackHistoryPendingOp::id),
            )
        }
        .sortedBy(PlaybackHistoryPendingOp::id)
}
