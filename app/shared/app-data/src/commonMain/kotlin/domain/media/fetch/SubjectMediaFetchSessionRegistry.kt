package com.wynime.app.domain.media.fetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.utils.platform.currentTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    fun release(session: MediaFetchSession) {
        scope.launch {
            lock.withLock {
                entries.firstOrNull { it.session === session }?.lastUsedMillis = nowMillis()
            }
        }
    }

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
