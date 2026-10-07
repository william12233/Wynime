package com.wynime.app.ui.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.download.DownloadOperation
import com.wynime.app.domain.media.download.DownloadOperations
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.utils.coroutines.childScope

class DownloadOperationRunnerTest {
    private class Fixture(testScope: TestScope, caches: List<MediaCache>) {
        val storage = FakeDownloadStorage().apply { listFlow.value = caches }
        val downloadManager = MediaDownloadManager(listOf(storage), testScope.backgroundScope)
        val deleteCache = FakeDeleteCacheUseCase(downloadManager)

        private val operationJob = SupervisorJob(testScope.backgroundScope.coroutineContext.job)
        val operations = DownloadOperations(
            downloadManager, deleteCache,
            CoroutineScope(testScope.backgroundScope.coroutineContext + operationJob),
        )
        val pendingOperationCount: Int get() = operationJob.children.count()

        val runnerScope = testScope.backgroundScope.childScope()
        val runner = DownloadOperationRunner(operations, runnerScope)
    }

    private fun TestScope.fixture(vararg caches: MediaCache): Fixture = Fixture(this, caches.toList()).also { runCurrent() }

    @Test
    fun `failures accumulate across batches and reset on dismiss`() = runTest {
        val failing = object : MediaCache by testDownloadCache(1) {
            override suspend fun pause() = throw IllegalStateException("pause failed")
        }
        val normal = testDownloadCache(2)
        val fixture = fixture(failing, normal)

        fixture.runner.run(setOf(failing.cacheId, normal.cacheId), DownloadOperation.Pause)
        fixture.runner.failedCount.first { it == 1 }
        assertEquals(MediaCacheState.PAUSED, normal.state.value)

        fixture.runner.run(setOf(failing.cacheId), DownloadOperation.Pause)
        fixture.runner.failedCount.first { it == 2 }

        fixture.runner.dismissFailures()
        assertEquals(0, fixture.runner.failedCount.value)

        fixture.deleteCache.failure = IllegalStateException("delete failed")
        fixture.runner.run(setOf(normal.cacheId), DownloadOperation.Delete)
        fixture.runner.failedCount.first { it == 1 }
        assertEquals(2, fixture.storage.listFlow.value.size)
    }

    @Test
    fun `empty ids submit nothing`() = runTest {
        val fixture = fixture(testDownloadCache(1))
        fixture.runner.run(emptySet(), DownloadOperation.Pause)
        assertEquals(0, fixture.pendingOperationCount)
        runCurrent()
        assertEquals(0, fixture.runner.failedCount.value)
    }

    @Test
    fun `closing the runner scope stops counting while the operation still completes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var pauseCalls = 0
        val failing = object : MediaCache by testDownloadCache(1) {
            override suspend fun pause() {
                pauseCalls++
                gate.await()
                throw IllegalStateException("pause failed")
            }
        }
        val fixture = fixture(failing)

        fixture.runner.run(setOf(failing.cacheId), DownloadOperation.Pause)
        runCurrent()
        assertEquals(1, pauseCalls)

        fixture.runnerScope.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, fixture.pendingOperationCount)
        assertEquals(0, fixture.runner.failedCount.value)
    }
}
