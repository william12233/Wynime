package com.wynime.utils.coroutines

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestartableCoroutineScopeTest {

    private val testScope = TestScope()
    private lateinit var scope: RestartableCoroutineScope

    @BeforeTest
    fun setUp() {
        scope = RestartableCoroutineScope(testScope.coroutineContext)
    }

    @Test
    fun `launch starts coroutine - it completes successfully`() = testScope.runTest {
        val result = CompletableDeferred<Int>()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            result.complete(42)
        }

        assertEquals(42, result.await())
        scope.close()
    }

    @Test
    fun `cancel stops running coroutines`() = testScope.runTest {
        val started = CompletableDeferred<Unit>()
        val isRunning = CompletableDeferred<Boolean>()

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } catch (_: Exception) {
                isRunning.complete(false)
            }
        }

        started.await()
        assertTrue(job.isActive)

        scope.restart()
        assertFalse(job.isActive)

        assertFalse(isRunning.await())

        scope.close()
    }

    @Test
    fun `can launch new coroutines after cancel`() = testScope.runTest {

        val job1Started = CompletableDeferred<Unit>()
        val job1 = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            job1Started.complete(Unit)
            awaitCancellation()
        }
        job1Started.await()

        scope.restart()
        assertFalse(job1.isActive)

        val result = CompletableDeferred<Int>()
        val job2 = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            result.complete(42)
        }

        assertEquals(42, result.await())
        assertNotEquals(job1, job2)

        scope.close()
    }

    @Test
    fun `close cancels all coroutines and allows new launches`() = testScope.runTest {
        val job1Started = CompletableDeferred<Unit>()
        val job1 = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            job1Started.complete(Unit)
            awaitCancellation()
        }
        job1Started.await()

        scope.close()
        assertFalse(job1.isActive)

        val job2 = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            awaitCancellation()
        }
        assertFalse(job2.isActive)

        scope.close()
    }

    @Test
    fun `restart doesnt affect parent scope`() = testScope.runTest {
        val parentJob = coroutineContext.job
        val initialActiveState = parentJob.isActive

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            awaitCancellation()
        }
        scope.restart()

        assertEquals(initialActiveState, parentJob.isActive)

        scope.close()
    }

    @Test
    fun `multiple launches work independently`() = testScope.runTest {

        val results = List(5) { CompletableDeferred<Int>() }

        results.forEachIndexed { index, deferred ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                deferred.complete(index)
            }
        }

        results.forEachIndexed { index, deferred ->
            assertEquals(index, deferred.await())
        }
        scope.close()
    }

    @Test
    fun `cancel affects only current child jobs`() = testScope.runTest {
        val beforeCancelResult = CompletableDeferred<Int>()
        val afterCancelResult = CompletableDeferred<Int>()

        val job1Started = CompletableDeferred<Unit>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            job1Started.complete(Unit)
            awaitCancellation()
        }

        job1Started.await()
        scope.restart()

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            afterCancelResult.complete(2)
        }

        assertFalse(beforeCancelResult.isCompleted)

        assertEquals(2, afterCancelResult.await())

        scope.close()
    }

    @Test
    fun `thread-safety - concurrent launches`() = testScope.runTest {
        val completedJobs = CompletableDeferred<Int>()
        var completed = 0
        val lock = SynchronizedObject()

        List(100) {

            launch(start = CoroutineStart.UNDISPATCHED) {
                synchronized(lock) {
                    completed++
                    if (completed == 100) {
                        completedJobs.complete(completed)
                    }
                }
            }
        }

        assertEquals(100, completedJobs.await())
        scope.close()
    }

    @Test
    fun `thread-safety - concurrent cancellations`() = testScope.runTest {

        val results = List(50) { CompletableDeferred<Boolean>() }
        results.forEach { deferred ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } catch (_: Exception) {

                }
            }
        }

        val cancellationJobs = List(10) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                scope.restart()

                scope.launch(start = CoroutineStart.UNDISPATCHED) {

                }
            }
        }

        cancellationJobs.forEach { it.join() }

        results.forEach { deferred ->
            assertFalse(deferred.isCompleted)
        }
        scope.close()
    }

    @Test
    fun `child job cancellation doesnt affect other children`() = testScope.runTest {
        val result2 = CompletableDeferred<Int>()
        val job2Run = CompletableDeferred<Unit>()

        val job1 = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            awaitCancellation()
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            job2Run.await()
            result2.complete(2)
        }

        job1.cancel()

        job2Run.complete(Unit)
        assertEquals(2, result2.await())

        scope.close()
    }

    @Test
    fun `cancelled scope doesnt leak memory`() = testScope.runTest {
        val deferreds = mutableListOf<CompletableDeferred<Unit>>()

        repeat(100) {
            val deferred = CompletableDeferred<Unit>()
            deferreds.add(deferred)

            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(start = CoroutineStart.ATOMIC) {
                try {
                    awaitCancellation()
                } finally {
                    deferred.complete(Unit)
                }
            }
        }

        scope.closeAndJoin()

        deferreds.forEach { deferred ->
            assertTrue(deferred.isCompleted)
        }
    }
}
