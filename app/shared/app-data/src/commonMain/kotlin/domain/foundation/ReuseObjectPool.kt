package com.wynime.app.domain.foundation

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.concurrent.Volatile

internal class ReuseObjectPool<K : Any, V>(
    private val newInstance: (K) -> V,
    private val onRelease: (V) -> Unit = {},
) {
    private data class Store<V>(
        val value: V,
    ) {
        val refCounter = atomic(0)
    }

    @Volatile
    private var map = mapOf<K, Store<V>>()

    private val mapLock = ReentrantLock()

    private fun borrowExisting(matrix: K): V? {
        val existingClient = map[matrix] ?: return null

        while (true) {
            val curr = existingClient.refCounter.value
            if (curr == 0) {

                return null
            }
            if (existingClient.refCounter.compareAndSet(curr, curr + 1)) {
                return existingClient.value
            } else {

            }
        }
    }

    fun borrow(matrix: K): V {
        borrowExisting(matrix)?.let { return it }
        mapLock.withLock {
            borrowExisting(matrix)?.let { return it }

            val newClient = newInstance(matrix)
            val store = Store(newClient)
            store.refCounter.incrementAndGet()
            map =
                map + (matrix to store)
            return newClient
        }
    }

    fun release(matrix: K, value: V) {
        val existing = map[matrix]
        checkNotNull(existing) { "Value $value (for matrix $matrix) not found in the map" }
        check(existing.value === value) { "Matrix $matrix has a corresponding value ${existing.value}, but does not equal to releasing value $value" }
        releaseOneReference(existing, matrix)
        return
    }

    private fun releaseOneReference(
        store: Store<V>,
        matrix: K,
    ) {
        while (true) {
            val curr = store.refCounter.value
            if (store.refCounter.compareAndSet(curr, curr - 1)) {
                if (curr == 1) {

                    mapLock.withLock {

                        if (map[matrix] === store) {

                            val newMap = map.toMutableMap()
                            newMap.remove(matrix)
                            map = newMap
                        }
                        onRelease(store.value)
                    }
                } else {

                }

                return
            } else {

            }
        }
    }

    @TestOnly
    fun forceReleaseAll() {
        mapLock.withLock {
            map.forEach { (_, store) ->
                if (store.refCounter.value != 0) {
                    store.refCounter.value = 0
                    onRelease(store.value)
                }
            }
            map = emptyMap()
        }
    }
}