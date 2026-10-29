package com.wynime.utils.httpdownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A fair, dynamically-sized limiter for complete media download transfers.
 *
 * A limit change never revokes an existing permit. Lowering the limit only
 * affects the next queued transfer; raising it immediately drains the queue.
 */
class DownloadConcurrencyLimiter(
    initialLimit: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS,
) {
    private val mutex = Mutex()
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

    private var limit = normalizeLimit(initialLimit)
    private var active = 0

    suspend fun <T> withPermit(block: suspend () -> T): T {
        val waiter = CompletableDeferred<Unit>()
        val acquiredImmediately = mutex.withLock {
            if (active < limit) {
                active++
                true
            } else {
                waiters.addLast(waiter)
                false
            }
        }

        if (!acquiredImmediately) {
            try {
                waiter.await()
            } catch (error: CancellationException) {
                val wasGranted = withContext(NonCancellable) {
                    mutex.withLock {
                        !waiters.remove(waiter)
                    }
                }
                if (wasGranted) {
                    withContext(NonCancellable) { release() }
                }
                throw error
            }
        }

        try {
            return block()
        } finally {
            withContext(NonCancellable) { release() }
        }
    }

    suspend fun updateLimit(newLimit: Int) {
        val toGrant = mutex.withLock {
            limit = normalizeLimit(newLimit)
            drainWaitersLocked()
        }
        toGrant.forEach { it.complete(Unit) }
    }

    private suspend fun release() {
        val toGrant = mutex.withLock {
            check(active > 0) { "Cannot release an inactive download permit" }
            active--
            drainWaitersLocked()
        }
        toGrant.forEach { it.complete(Unit) }
    }

    private fun drainWaitersLocked(): List<CompletableDeferred<Unit>> {
        if (active >= limit || waiters.isEmpty()) return emptyList()

        val toGrant = buildList {
            while (active < limit && waiters.isNotEmpty()) {
                active++
                add(waiters.removeFirst())
            }
        }
        return toGrant
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2

        fun normalizeLimit(value: Int): Int = value.coerceAtLeast(1)
    }
}
