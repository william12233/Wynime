package com.wynime.app.domain.settings

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServiceConnectionTesterTest {

    private fun createService(
        id: String,
        shouldThrow: Boolean = false,
        shouldFail: Boolean = false,
        onTestCalled: suspend (ContinuationInterceptor) -> Unit = {},
        signal: CompletableDeferred<Unit>? = null,
    ): ServiceConnectionTester.Service {
        return ServiceConnectionTester.Service(
            id = id,
            test = {
                onTestCalled(currentContinuationInterceptor())

                signal?.await()

                if (shouldThrow) {
                    throw IllegalStateException("Test error")
                }
                !shouldFail
            }
        )
    }

    private suspend fun currentContinuationInterceptor() =
        currentCoroutineContext()[ContinuationInterceptor]!!

    @Test
    fun `testAll - single service success`() = runTest {
        val service = createService("service-id")
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        val state = results.states[service]
        assertTrue(state is ServiceConnectionTester.TestState.Success)
    }

    @Test
    fun `testAll - single service failure`() = runTest {
        val service = createService("fail-service", shouldFail = true)
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        val state = results.states[service]
        assertTrue(state is ServiceConnectionTester.TestState.Failed)
    }

    @Test
    fun `testAll - single service error`() = runTest {
        val service = createService("error-service", shouldThrow = true)
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        val state = results.states[service]
        assertTrue(state is ServiceConnectionTester.TestState.Error)
        assertTrue(state.e is IllegalStateException)
    }

    @Test
    fun `testAll - multiple services mixed results`() = runTest {
        val okService = createService("ok")
        val failService = createService("fail", shouldFail = true)
        val errorService = createService("error", shouldThrow = true)

        val tester = ServiceConnectionTester(
            services = listOf(okService, failService, errorService),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        val results = tester.results.first()
        assertTrue(results.states[okService] is ServiceConnectionTester.TestState.Success)
        assertTrue(results.states[failService] is ServiceConnectionTester.TestState.Failed)
        assertTrue(results.states[errorService] is ServiceConnectionTester.TestState.Error)
    }

    @Test
    fun `testAll - cancel during testing - states revert to Idle`() = runTest {

        val serviceSignal = CompletableDeferred<Unit>()
        val longRunningService = createService(
            "long-run",
            signal = serviceSignal,
        )

        val tester = ServiceConnectionTester(
            services = listOf(longRunningService),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.results.test {

            val initial = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[longRunningService])

            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                tester.testAll()
            }

            val testing = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Testing, testing.states[longRunningService])

            job.cancelAndJoin()

            val final = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, final.states[longRunningService])

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopAll - states are kept intact`() = runTest {
        val service1 = createService("s1")
        val service2 = createService("s2", shouldFail = true)

        val tester = ServiceConnectionTester(
            services = listOf(service1, service2),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()
        val results = tester.results.first()
        assertNotEquals(ServiceConnectionTester.TestState.Idle, results.states[service1])
        assertNotEquals(ServiceConnectionTester.TestState.Idle, results.states[service2])

        tester.stopAll()
        val newResults = tester.results.first()
        assertEquals(results.states, newResults.states)
    }

    @Test
    fun `results flow - verifies states update in real-time`() = runTest {

        val serviceSignal = CompletableDeferred<Unit>()
        val service = createService(
            "service",
            signal = serviceSignal,
        )
        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.results.test {

            val initial = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[service])

            val job = launch(start = CoroutineStart.UNDISPATCHED) { tester.testAll() }

            val testing = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Testing, testing.states[service])

            serviceSignal.complete(Unit)

            val final = awaitItem()
            assertTrue(final.states[service] is ServiceConnectionTester.TestState.Success)

            job.cancelAndJoin()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `results flow - verifies multiple states update in real-time`() = runTest {

        val service1Signal = CompletableDeferred<Unit>()
        val service2Signal = CompletableDeferred<Unit>()

        val service1 = createService("service1", signal = service1Signal)
        val service2 = createService("service2", signal = service2Signal)

        val tester = ServiceConnectionTester(
            services = listOf(service1, service2),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.results.test {

            val initial = awaitItem()
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[service1])
            assertEquals(ServiceConnectionTester.TestState.Idle, initial.states[service2])

            val job = launch(start = CoroutineStart.UNDISPATCHED) { tester.testAll() }

            var bothAreTesting = false
            while (!bothAreTesting) {
                val next = awaitItem()
                val s1State = next.states[service1]
                val s2State = next.states[service2]
                if (s1State == ServiceConnectionTester.TestState.Testing &&
                    s2State == ServiceConnectionTester.TestState.Testing
                ) {
                    bothAreTesting = true
                }
            }

            service2Signal.complete(Unit)

            val afterService2 = awaitItem()
            assertTrue(afterService2.states[service2] is ServiceConnectionTester.TestState.Success)
            assertTrue(afterService2.states[service1] is ServiceConnectionTester.TestState.Testing)

            service1Signal.complete(Unit)

            val final = awaitItem()
            assertTrue(final.states[service1] is ServiceConnectionTester.TestState.Success)
            assertTrue(final.states[service2] is ServiceConnectionTester.TestState.Success)

            job.cancelAndJoin()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `testAll - uses provided defaultDispatcher`() = runTest {
        val interceptorFlow = MutableStateFlow<ContinuationInterceptor?>(null)
        val service = createService(
            id = "capture-dispatcher",
            onTestCalled = { interceptor ->
                interceptorFlow.value = interceptor
            }
        )

        val tester = ServiceConnectionTester(
            services = listOf(service),
            defaultDispatcher = currentContinuationInterceptor(),
        )

        tester.testAll()

        assertEquals(currentContinuationInterceptor(), interceptorFlow.value)
    }

}
