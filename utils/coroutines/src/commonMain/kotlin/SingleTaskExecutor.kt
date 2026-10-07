package com.wynime.utils.coroutines

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface SingleTaskExecutor {

    suspend operator fun <R> invoke(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> R,
    ): R

    fun cancelCurrent()
}

fun SingleTaskExecutor(parentCoroutineContext: CoroutineContext = EmptyCoroutineContext): SingleTaskExecutor =
    AtomicSingleTaskExecutor(parentCoroutineContext)

class AtomicSingleTaskExecutor(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : SingleTaskExecutor {
    private val _job = atomic<Job?>(null)
    private val scope = CoroutineScope(coroutineContext)

    @TestOnly
    internal fun getJob(): Job? = _job.value

    override suspend fun <R> invoke(
        coroutineContext: CoroutineContext,
        block: suspend CoroutineScope.() -> R,
    ): R {

        val previousJob = _job.value
        previousJob?.cancel()

        val continuationInterceptor =
            coroutineContext[ContinuationInterceptor]
                ?: currentCoroutineContext()[ContinuationInterceptor]
                ?: Dispatchers.Default

        val newJob = scope.async(
            coroutineContext + currentCoroutineContext()[Job]!! + continuationInterceptor,
            start = CoroutineStart.LAZY,
            block = block,
        )

        if (_job.compareAndSet(previousJob, newJob)
        ) {

            if (newJob.start()) {

            } else {

                newJob.checkCancelledAndThrowCancellation()
            }

            try {
                return newJob.await()
            } finally {

                _job.compareAndSet(newJob, null)
            }
        } else {

            newJob.cancel()
            newJob.checkCancelledAndThrowCancellation()
        }
    }

    override fun cancelCurrent() {
        val previousJob = _job.value
        previousJob?.cancel()
        _job.compareAndSet(previousJob, null)
    }

    private suspend fun Job.checkCancelledAndThrowCancellation(): Nothing {
        check(isCancelled) { "newJob is not cancelled" }
        throwCancellation()
    }

    private suspend fun Job.throwCancellation(): Nothing {

        join()

        throw CancellationException("AtomicSingleTaskExecutor.invoke is superseded by a new invocation")
    }
}
