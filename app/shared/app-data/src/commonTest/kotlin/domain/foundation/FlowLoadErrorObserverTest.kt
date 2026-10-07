package com.wynime.app.domain.foundation

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FlowLoadErrorObserverTest {

    @Test
    fun `success flow does not update loadErrorState`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flowOf("value").catchLoadError(observer)

        flow.test {
            assertEquals("value", awaitItem())
            awaitComplete()
        }

        assertNull(observer.loadErrorState.value, "Expected no error for a success flow.")
    }

    @Test
    fun `flow emits an exception updates loadErrorState`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flow {
            emit("value1")
            throw IllegalStateException("Test exception")
        }.catchLoadError(observer)

        flow.test {

            assertEquals("value1", awaitItem())

            awaitError()
        }

        val error = observer.loadErrorState.value
        assertIs<LoadError.UnknownError>(error, "Expected an UnknownError for a generic exception.")
        assertIs<IllegalStateException>(error.throwable, "Should wrap the original exception.")
        assertEquals("Test exception", error.throwable!!.message)
    }

    @Test
    fun `flow throws CancellationException does NOT update loadErrorState`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flow {
            emit("value1")
            throw CancellationException("Simulated cancellation")
        }.catchLoadError(observer)

        flow.test {

            assertEquals("value1", awaitItem())

            val exception = awaitError()
            assertIs<CancellationException>(exception)
        }

        assertNull(observer.loadErrorState.value, "Should not capture a LoadError for CancellationExceptions.")
    }

    @Test
    fun `flow completes successfully after emission - no error`() = runTest {
        val observer = FlowLoadErrorObserver()
        val flow = flow {
            emit("value1")
            emit("value2")

        }.catchLoadError(observer)

        flow.test {
            assertEquals("value1", awaitItem())
            assertEquals("value2", awaitItem())
            awaitComplete()
        }

        assertNull(observer.loadErrorState.value, "No error should be set if the flow completes normally.")
    }

    @Test
    fun `flow resets error state on new emission`() = runTest {

        val observer = FlowLoadErrorObserver()

        val failingFlow = flow {
            emit("value")
            throw IllegalArgumentException("Test fail")
        }.catchLoadError(observer)

        val successFlow = flowOf("new-value").catchLoadError(observer)

        failingFlow.test {
            assertEquals("value", awaitItem())
            awaitError()
        }

        assertIs<LoadError.UnknownError>(observer.loadErrorState.value)

        successFlow.test {

            assertEquals("new-value", awaitItem())
            awaitComplete()
        }

        assertNull(observer.loadErrorState.value, "Error should be reset after a successful emission.")
    }
}