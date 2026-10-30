package com.wynime.utils.httpdownloader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadConcurrencyLimiterTest {
    @Test
    fun `limits active transfers and preserves queue order`() = runTest {
        val limiter = DownloadConcurrencyLimiter(initialLimit = 1)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val completed = mutableListOf<Int>()

        val first = launch {
            limiter.withPermit {
                firstStarted.complete(Unit)
                releaseFirst.await()
                completed += 1
            }
        }
        val second = launch {
            limiter.withPermit {
                secondStarted.complete(Unit)
                completed += 2
            }
        }

        firstStarted.await()
        assertFalse(secondStarted.isCompleted)

        releaseFirst.complete(Unit)
        secondStarted.await()
        first.join()
        second.join()

        assertEquals(listOf(1, 2), completed)
    }

    @Test
    fun `lowering the limit does not interrupt active transfers`() = runTest {
        val limiter = DownloadConcurrencyLimiter(initialLimit = 2)
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val thirdStarted = CompletableDeferred<Unit>()

        val first = launch {
            limiter.withPermit {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        val second = launch {
            limiter.withPermit {
                secondStarted.complete(Unit)
                releaseSecond.await()
            }
        }
        val third = launch {
            limiter.withPermit {
                thirdStarted.complete(Unit)
            }
        }

        firstStarted.await()
        secondStarted.await()
        limiter.updateLimit(1)

        releaseFirst.complete(Unit)
        first.join()
        assertFalse(thirdStarted.isCompleted)

        releaseSecond.complete(Unit)
        second.join()
        thirdStarted.await()
        third.join()
    }

    @Test
    fun `raising the limit immediately starts queued transfers`() = runTest {
        val limiter = DownloadConcurrencyLimiter(initialLimit = 1)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        val first = launch {
            limiter.withPermit {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        val second = launch {
            limiter.withPermit {
                secondStarted.complete(Unit)
            }
        }

        firstStarted.await()
        yield()
        assertFalse(secondStarted.isCompleted)

        limiter.updateLimit(2)
        secondStarted.await()
        second.join()

        releaseFirst.complete(Unit)
        first.join()
    }

    @Test
    fun `cancelling a queued transfer releases no permit`() = runTest {
        val limiter = DownloadConcurrencyLimiter(initialLimit = 1)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch {
            limiter.withPermit {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()

        val queued = launch {
            limiter.withPermit { error("cancelled transfer must not start") }
        }
        yield()
        queued.cancel()
        queued.join()

        releaseFirst.complete(Unit)
        first.join()

        var ran = false
        limiter.withPermit { ran = true }
        assertTrue(ran)
    }
}
