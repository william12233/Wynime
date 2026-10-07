package com.wynime.app.data.repository.subject

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BangumiSyncCoordinatorTest {
    @Test
    fun `same operation shares one running job and terminal state`() = runTest {
        val coordinator = BangumiSyncCoordinator()
        val gate = CompletableDeferred<Unit>()
        var executions = 0

        val first = async {
            coordinator.withExclusive(BangumiSyncOperation.TRACKING) {
                executions++
                gate.await()
                42
            }
        }
        runCurrent()
        val second = async {
            coordinator.withExclusive(BangumiSyncOperation.TRACKING) {
                executions++
                99
            }
        }
        runCurrent()

        assertIs<BangumiSyncUiState.Running>(coordinator.state.value)
        gate.complete(Unit)

        assertEquals(42, first.await())
        assertEquals(42, second.await())
        assertEquals(1, executions)
        assertIs<BangumiSyncUiState.Completed>(coordinator.state.value)
    }

    @Test
    fun `exception enters failed state and a later operation can run`() = runTest {
        val coordinator = BangumiSyncCoordinator()

        assertFailsWith<IllegalStateException> {
            coordinator.withExclusive(BangumiSyncOperation.COLLECTION_REFRESH) {
                error("page failed")
            }
        }
        val failed = assertIs<BangumiSyncUiState.Failed>(coordinator.state.value)
        assertEquals(BangumiSyncPhase.FAILED, failed.progress.phase)

        assertEquals(
            7,
            coordinator.withExclusive(BangumiSyncOperation.COLLECTION_REFRESH) { 7 },
        )
        assertIs<BangumiSyncUiState.Completed>(coordinator.state.value)
    }
}
