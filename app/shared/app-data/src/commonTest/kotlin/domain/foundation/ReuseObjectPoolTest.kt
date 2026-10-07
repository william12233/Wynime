package com.wynime.app.domain.foundation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.fetch.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class ReuseObjectPoolTest {

    @Test
    fun `single borrow and release should create and then remove instance`() {
        val createdCount = AtomicInteger(0)
        val releasedCount = AtomicInteger(0)

        val pool = ReuseObjectPool<String, String>(
            newInstance = { key ->
                createdCount.incrementAndGet()
                "ClientFor_$key"
            },
            onRelease = {
                releasedCount.incrementAndGet()
            },
        )

        val instance = pool.borrow("matrixA")
        assertEquals(1, createdCount.get())
        assertEquals("ClientFor_matrixA", instance)

        pool.release("matrixA", instance)
        assertEquals(1, releasedCount.get())
    }

    @Test
    fun `borrowing same key multiple times returns same instance`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )

        val client1 = pool.borrow("matrixA")
        val client2 = pool.borrow("matrixA")
        assertSame(client1, client2, "Should be same instance for the same key")

        pool.release("matrixA", client1)
        pool.release("matrixA", client2)
    }

    @Test
    fun `refCount reaches zero after correct number of releases`() {
        val releasedCount = AtomicInteger(0)

        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
            onRelease = { releasedCount.incrementAndGet() },
        )

        val c1 = pool.borrow("matrixA")
        val c2 = pool.borrow("matrixA")
        assertSame(c1, c2)

        pool.release("matrixA", c1)
        assertEquals(0, releasedCount.get())

        pool.release("matrixA", c2)
        assertEquals(1, releasedCount.get(), "onRelease must have been called exactly once")
    }

    @Test
    fun `concurrency test - multiple borrows and releases`() = runTest {
        val createdCount = AtomicInteger(0)
        val releasedCount = AtomicInteger(0)

        val pool = ReuseObjectPool<String, String>(
            newInstance = {
                createdCount.incrementAndGet()
                "client_for_$it"
            },
            onRelease = { releasedCount.incrementAndGet() },
        )

        val nCoroutines = 50
        val nIterationsPerCoroutine = 100

        coroutineScope {
            repeat(nCoroutines) {
                launch(Dispatchers.Default) {
                    repeat(nIterationsPerCoroutine) {
                        val client = pool.borrow("matrixA")

                        pool.release("matrixA", client)
                    }
                }
            }
        }

        val c = pool.borrow("matrixA")
        pool.release("matrixA", c)

        assertTrue(releasedCount.get() >= 1, "Should have released at least once")
    }

    @Test
    fun `concurrency test - multiple keys remain distinct`() = runTest {
        val pool = ReuseObjectPool<Int, String>(
            newInstance = { "client_for_$it" },
        )

        val keyCount = 5
        val concurrencyPerKey = 20

        coroutineScope {
            (0 until keyCount).map { key ->
                launch(Dispatchers.Default) {
                    repeat(concurrencyPerKey) {
                        val c = pool.borrow(key)
                        assertEquals("client_for_$key", c, "Should match expected pooled instance")
                        pool.release(key, c)
                    }
                }
            }.joinAll()
        }
    }

    @Test
    fun `releasing key that does not exist throws`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )

        val instance = pool.borrow("matrixA")
        pool.release("matrixA", instance)

        val ex = assertFailsWith<IllegalStateException> {
            pool.release("unknownKey", "fakeClient")
        }
        assertTrue(
            ex.message?.contains("not found in the map") == true,
            "Expected error about key not found",
        )
    }

    @Test
    fun `releasing value that does not match the stored value throws`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )
        val instance = pool.borrow("matrixA")

        val ex = assertFailsWith<IllegalStateException> {
            pool.release("matrixA", "not_the_same_instance")
        }
        assertTrue(
            ex.message?.contains("does not equal to releasing value") == true,
            "Expected error about mismatched value",
        )
    }

    @Test
    fun `borrow after fully released returns a brand new instance`() {
        val pool = ReuseObjectPool<String, Any>(
            newInstance = { Any() },
        )

        val instance1 = pool.borrow("matrixA")

        pool.release("matrixA", instance1)

        val instance2 = pool.borrow("matrixA")

        assertNotSame(instance1, instance2, "Should be a new instance after refCount reached 0")
    }

    @Test
    fun `releasing multiple times with one borrow triggers an exception on the second release`() {
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
        )
        val instance = pool.borrow("matrixA")

        pool.release("matrixA", instance)

        val ex = assertFailsWith<IllegalStateException> {
            pool.release("matrixA", instance)
        }
        assertTrue(
            ex.message?.contains("Value client_for_matrixA (for matrix matrixA) not found in the map") == true,
            "Expected error about value not found (since it was already removed)",
        )
    }

    @Test
    fun `onRelease throws exception does not corrupt pool`() = runTest {
        var onReleaseCalled = false
        val pool = ReuseObjectPool<String, String>(
            newInstance = { "client_for_$it" },
            onRelease = {
                onReleaseCalled = true
                error("Simulated failure in onRelease")
            },
        )

        val instance = pool.borrow("matrixA")
        val ex = assertFailsWith<IllegalStateException> {
            pool.release("matrixA", instance)
        }
        assertEquals("Simulated failure in onRelease", ex.message)
        assertTrue(onReleaseCalled)
    }
}