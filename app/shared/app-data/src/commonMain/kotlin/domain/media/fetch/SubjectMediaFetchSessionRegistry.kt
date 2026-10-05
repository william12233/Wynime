/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Shares one active subject-level discovery session between playback and download.
 *
 * Entries are deliberately short-lived. This is a discovery snapshot, not a permanent cache:
 * a provider session can be reused while the user moves between play/download actions, and it is
 * rebuilt after expiry or explicit invalidation. The session keeps collecting so a slow provider
 * cannot disappear just because the selector UI changed collectors.
 */
class SubjectMediaFetchSessionRegistry(
    private val scope: CoroutineScope,
    private val createSession: suspend (MediaFetchRequest) -> MediaFetchSession,
    private val expiry: Duration = 90.seconds,
    private val nowMillis: () -> Long = ::currentTimeMillis,
) : AutoCloseable {
    private data class Entry(
        val request: MediaFetchRequest,
        val session: MediaFetchSession,
        val subscription: Job,
        var lastUsedMillis: Long,
    )

    private val lock = Mutex()
    private val entries = mutableListOf<Entry>()
    private val cleanupJob = scope.launch {
        val interval = (expiry.inWholeMilliseconds / 2).coerceIn(1_000L, 30_000L)
        while (isActive) {
            delay(interval)
            evictExpired()
        }
    }

    init {
        require(expiry.isPositive()) { "expiry must be positive" }
    }

    /** Returns an active snapshot for the same subject query, or starts one. */
    suspend fun get(request: MediaFetchRequest): MediaFetchSession = lock.withLock {
        val now = nowMillis()
        evictExpiredLocked(now)
        entries.firstOrNull { it.request.isSameSubjectQuery(request) }?.let { entry ->
            entry.lastUsedMillis = now
            retryFailedSources(entry.session)
            return@withLock entry.session
        }

        val session = createSession(request)
        val entry = Entry(
            request = request,
            session = session,
            subscription = scope.launch { session.cumulativeResults.collect() },
            lastUsedMillis = now,
        )
        entries += entry
        session
    }

    /** Keeps a snapshot alive for the remainder of the short expiry window. */
    fun release(session: MediaFetchSession) {
        scope.launch {
            lock.withLock {
                entries.firstOrNull { it.session === session }?.lastUsedMillis = nowMillis()
            }
        }
    }

    /** Forces a new discovery snapshot for the subject query. */
    suspend fun invalidate(request: MediaFetchRequest) = lock.withLock {
        val removed = entries.filter { it.request.isSameSubjectQuery(request) }
        entries.removeAll(removed.toSet())
        removed.forEach { it.subscription.cancel() }
    }

    private suspend fun evictExpired() = lock.withLock {
        evictExpiredLocked(nowMillis())
    }

    private fun evictExpiredLocked(now: Long) {
        val expired = entries.filter { now - it.lastUsedMillis >= expiry.inWholeMilliseconds }
        entries.removeAll(expired.toSet())
        expired.forEach { it.subscription.cancel() }
    }

    private fun retryFailedSources(session: MediaFetchSession) {
        for (result in session.mediaSourceResults) {
            if (result.state.value.isFailedOrAbandoned) {
                result.restart()
            }
        }
    }

    override fun close() {
        cleanupJob.cancel()
        entries.forEach { it.subscription.cancel() }
        entries.clear()
    }
}
