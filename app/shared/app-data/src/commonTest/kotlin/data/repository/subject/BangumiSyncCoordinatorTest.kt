/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the
 * following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

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
