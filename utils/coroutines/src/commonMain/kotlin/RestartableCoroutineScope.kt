package com.wynime.utils.coroutines

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class RestartableCoroutineScope(
    private val parentContext: CoroutineContext = EmptyCoroutineContext
) {
    @Volatile
    private var scope: CoroutineScope = newScope()
    private val lock = ReentrantLock()

    val currentCoroutineContext: CoroutineContext
        get() = lock.withLock {
            scope.coroutineContext
        }

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        lock.withLock {
            return scope.launch(context, start, block)
        }
    }

    fun restart() {
        lock.withLock {
            scope.cancel()
            scope = newScope()
        }
    }

    private fun newScope(): CoroutineScope = parentContext.childScope()

    fun close() {
        lock.withLock {
            scope.cancel()
        }
    }

    suspend fun closeAndJoin() {
        val scope = lock.withLock {
            scope.cancel()
            scope
        }
        scope.coroutineContext[Job]!!.join()
    }
}