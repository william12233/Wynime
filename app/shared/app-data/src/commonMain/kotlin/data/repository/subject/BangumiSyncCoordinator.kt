package com.wynime.app.data.repository.subject

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

enum class BangumiSyncOperation {
    TRACKING,
    COLLECTION_REFRESH,
    COLLECTION_PAGE_REFRESH,
    AUTO,
}

enum class BangumiSyncPhase {
    CONNECTING,
    FETCHING_COLLECTIONS,
    FETCHING_EPISODES,
    MERGING,
    APPLYING_REMOTE,
    APPLYING_LOCAL,
    RELOADING,
    COMPLETED,
    PARTIAL_FAILURE,
    FAILED,
}

data class BangumiSyncProgress(
    val operation: BangumiSyncOperation,
    val phase: BangumiSyncPhase,
    val current: Int,
    val total: Int?,
    val failedCount: Int = 0,
    val detail: String? = null,
)

sealed interface BangumiSyncUiState {
    data object Idle : BangumiSyncUiState

    data class Running(val progress: BangumiSyncProgress) : BangumiSyncUiState

    data class Completed(
        val progress: BangumiSyncProgress,
    ) : BangumiSyncUiState

    data class PartialFailure(
        val progress: BangumiSyncProgress,
    ) : BangumiSyncUiState

    data class Failed(
        val progress: BangumiSyncProgress,
        val error: String,
    ) : BangumiSyncUiState
}

class BangumiSyncCoordinator {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<BangumiSyncUiState>(BangumiSyncUiState.Idle)
    private var active: ActiveOperation? = null

    val state: StateFlow<BangumiSyncUiState> = _state.asStateFlow()

    suspend fun <T> withExclusive(
        operation: BangumiSyncOperation,
        block: suspend () -> T,
    ): T {
        while (true) {
            val claim = mutex.withLock {
                active?.let { running ->
                    if (running.operation == operation) {
                        Claim.Await(running.completion)
                    } else {
                        Claim.Wait(running.completion)
                    }
                } ?: run {
                    val running = ActiveOperation(
                        operation = operation,
                        completion = CompletableDeferred(),
                    )
                    active = running
                    _state.value = BangumiSyncUiState.Running(
                        BangumiSyncProgress(
                            operation = operation,
                            phase = BangumiSyncPhase.CONNECTING,
                            current = 0,
                            total = null,
                        ),
                    )
                    Claim.Start(running)
                }
            }

            when (claim) {
                is Claim.Await -> {
                    @Suppress("UNCHECKED_CAST")
                    return claim.completion.await() as T
                }

                is Claim.Wait -> {
                    try {
                        claim.completion.await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {

                    }
                }

                is Claim.Start -> {
                    try {
                        val result = block()
                        if (_state.value is BangumiSyncUiState.Running) {
                            complete(operation)
                        }
                        claim.operation.completion.complete(result)
                        return result
                    } catch (e: CancellationException) {
                        if (_state.value is BangumiSyncUiState.Running) {
                            fail(operation, e.message ?: "同步已取消")
                        }
                        claim.operation.completion.completeExceptionally(e)
                        throw e
                    } catch (e: Throwable) {
                        if (_state.value is BangumiSyncUiState.Running) {
                            fail(operation, e.message ?: e::class.simpleName.orEmpty())
                        }
                        claim.operation.completion.completeExceptionally(e)
                        throw e
                    } finally {
                        mutex.withLock {
                            if (active === claim.operation) active = null
                        }
                    }
                }
            }
        }
    }

    fun report(
        operation: BangumiSyncOperation,
        phase: BangumiSyncPhase,
        current: Int,
        total: Int? = null,
        failedCount: Int = 0,
        detail: String? = null,
    ) {
        if (_state.value !is BangumiSyncUiState.Running) return
        _state.value = BangumiSyncUiState.Running(
            BangumiSyncProgress(operation, phase, current, total, failedCount, detail),
        )
    }

    fun complete(operation: BangumiSyncOperation, detail: String? = null) {
        val current = currentProgress(operation)
        val progress = current.copy(
            phase = BangumiSyncPhase.COMPLETED,
            current = current.total ?: current.current,
            detail = detail ?: current.detail,
        )
        _state.value = BangumiSyncUiState.Completed(progress)
    }

    fun partialFailure(
        operation: BangumiSyncOperation,
        failedCount: Int,
        detail: String? = null,
    ) {
        val progress = currentProgress(operation).copy(
            phase = BangumiSyncPhase.PARTIAL_FAILURE,
            failedCount = failedCount,
            detail = detail ?: currentProgress(operation).detail,
        )
        _state.value = BangumiSyncUiState.PartialFailure(progress)
    }

    private fun fail(operation: BangumiSyncOperation, error: String) {
        val progress = currentProgress(operation).copy(
            phase = BangumiSyncPhase.FAILED,
            detail = error,
        )
        _state.value = BangumiSyncUiState.Failed(progress, error)
    }

    private fun currentProgress(operation: BangumiSyncOperation): BangumiSyncProgress {
        return (_state.value as? BangumiSyncUiState.Running)?.progress?.takeIf { it.operation == operation }
            ?: BangumiSyncProgress(operation, BangumiSyncPhase.CONNECTING, 0, null)
    }

    private class ActiveOperation(
        val operation: BangumiSyncOperation,
        val completion: CompletableDeferred<Any?>,
    )

    private sealed interface Claim {
        data class Start(val operation: ActiveOperation) : Claim
        data class Await(val completion: CompletableDeferred<Any?>) : Claim
        data class Wait(val completion: CompletableDeferred<Any?>) : Claim
    }
}
