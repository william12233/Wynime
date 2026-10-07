package com.wynime.utils.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

interface SuspendLazy<T> {
    val isInitialized: Boolean

    suspend fun get(): T

    fun getCompletedOrNull(): T?
}

fun <T> SuspendLazy<T>.asFlow(): Flow<T> = flow {
    emit(get())
}

fun <T> SuspendLazy(
    initializer: suspend () -> T
): SuspendLazy<T> = SuspendLazyImpl(initializer)

class SuspendLazyImpl<T>(
    initializer: suspend () -> T,
) : SuspendLazy<T> {
    @Volatile
    private var initialized = false
    private var initializer: (suspend () -> T)? = initializer

    override val isInitialized: Boolean get() = initialized

    @Volatile
    private var value: T? = null

    private val lock = Mutex()

    @Suppress("UNCHECKED_CAST")
    override suspend fun get(): T {
        if (initialized) return value as T

        lock.withLock {
            if (initialized) return value as T
            val initializer = initializer ?: error("initializer should not be null")
            value = initializer()
            this.initializer = null
            initialized = true
        }
        return value as T
    }

    override fun getCompletedOrNull(): T? = value
}