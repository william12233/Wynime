package com.wynime.utils.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AtomicSingleTaskExecutorTest {
    @Suppress("TestFunctionName")
    private fun AtomicSingleTaskExecutor(scope: TestScope): AtomicSingleTaskExecutor =
        AtomicSingleTaskExecutor(scope.coroutineContext)

    @Test
    fun `invoke - single call - runs to completion`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)

        var sideEffect = false
        executor.invoke {
            sideEffect = true
        }

        assertTrue(sideEffect, "The block should have executed.")
        assertNull(executor.getJob(), "Job should be cleared after completion.")
    }

    @Test
    fun `invoke - second call cancels first if still running`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)

        val job1Started = CompletableDeferred<Unit>()
        val job1Done = async(start = CoroutineStart.UNDISPATCHED) {
            executor.invoke {
                job1Started.complete(Unit)
                awaitCancellation()
            }
            @Suppress("UNREACHABLE_CODE")
            fail("Job should have been canceled.")
        }

        job1Started.await()

        var secondTaskRan = false
        executor.invoke {
            secondTaskRan = true
        }

        assertTrue(job1Done.isCancelled, "First job should be canceled.")
        assertTrue(secondTaskRan, "Second invocation should have run to completion.")
        assertNull(executor.getJob(), "Job should be cleared after second call completes.")
    }

    @Test
    fun `invoke - multiple concurrent calls - newest overshadows older`() = runTest(StandardTestDispatcher()) {
        val testScope = this
        val executor = AtomicSingleTaskExecutor(testScope)

        val started = mutableListOf<Int>()
        val finished = mutableListOf<Int>()

        suspend fun runIndexedTask(index: Int) {
            executor.invoke {
                started += index

                delay(100.milliseconds)
                finished += index
            }
        }

        val tasks = (1..5).map { i ->
            testScope.async {
                runIndexedTask(i)
            }
        }

        testScope.advanceUntilIdle()
        tasks.forEach { it.join() }

        println("Tasks started: $started")
        println("Tasks finished: $finished")

        assertTrue(5 in finished, "Newest invocation (5) must finish.")

        assertNull(executor.getJob(), "Job should be cleared after everything finishes.")
    }

    @Test
    fun `invoke - job is cleared after normal completion`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)
        assertNull(executor.getJob(), "Initially, no job should be set.")

        executor.invoke {

        }

        assertNull(executor.getJob(), "Job should be cleared after normal completion.")
    }

    @Test
    fun `invoke - old invocation does not overshadow new invocation`() = runTest(StandardTestDispatcher()) {

        val executor = AtomicSingleTaskExecutor(this)

        var olderRan = false
        var newerRan = false

        val older = async {

            executor.invoke {
                olderRan = true
                delay(500)
            }
        }

        delay(100)

        val newer = async(start = CoroutineStart.UNDISPATCHED) {
            executor.invoke {
                newerRan = true

            }
        }

        advanceUntilIdle()

        assertFailsWith<CancellationException> {
            older.await()
        }
        newer.await()

        assertTrue(newerRan, "The newer invocation should definitely run.")

    }

    @Test
    fun `cancelCurrent - no active job - does nothing`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)

        assertNull(executor.getJob(), "No job should be set initially.")

        executor.cancelCurrent()
        assertNull(executor.getJob(), "Still no job after cancel.")
    }

    @Test
    fun `cancelCurrent - running job gets canceled`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)
        val jobRunningStarted = async(start = CoroutineStart.UNDISPATCHED) {
            executor.invoke {

                awaitCancellation()
            }
        }

        val jobBeforeCancel = executor.getJob()
        assertNotNull(jobBeforeCancel, "A job should be running before cancel.")

        executor.cancelCurrent()
        assertNull(executor.getJob(), "Job should be cleared after cancel.")

        jobRunningStarted.join()
        assertTrue(jobRunningStarted.isCancelled, "Long-running job should be canceled.")
    }

    @Test
    fun `cancelCurrent - already completed job - does not throw or re-cancel`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)

        executor.invoke {

        }

        assertNull(executor.getJob(), "Job should already be null after completion.")

        executor.cancelCurrent()

        assertNull(executor.getJob(), "Calling cancel on a completed (null) job does nothing.")
    }

    @Test
    fun `cancelCurrent - multiple calls in a row - no issues`() = runTest {
        val executor = AtomicSingleTaskExecutor(this)

        val longJob = async(start = CoroutineStart.UNDISPATCHED) {
            executor.invoke {
                awaitCancellation()
            }
        }

        executor.cancelCurrent()
        executor.cancelCurrent()
        executor.cancelCurrent()

        longJob.join()

        assertTrue(longJob.isCancelled, "Job should be canceled after repeated executor.cancel() calls.")
        assertNull(executor.getJob(), "Job reference should be cleared.")

        var ranNewJob = false
        executor.invoke {
            ranNewJob = true
        }
        assertTrue(ranNewJob, "Executor should still function for future calls after repeated cancels.")
        assertNull(executor.getJob(), "New job should be cleared after completion.")
    }

    @Test
    fun `cancelCurrent - does not affect scope cancellation`() = runTest {
        val externalExecutor = AtomicSingleTaskExecutor(this)

        externalExecutor.cancelCurrent()

        var sideEffect = false
        externalExecutor.invoke {
            sideEffect = true
        }
        assertTrue(sideEffect, "Executor can still run if the underlying scope isn't canceled.")
    }

    @Test
    fun `cancelCurrent - concurrent call overshadow check`() = runTest {

        val executor = AtomicSingleTaskExecutor(this)
        var blockRan = false

        executor.cancelCurrent()

        executor.invoke {
            blockRan = true
        }

        assertTrue(blockRan, "The new block should run even if we called cancel just before.")
        assertNull(executor.getJob(), "Job reference should be cleared after completion.")
    }
}
