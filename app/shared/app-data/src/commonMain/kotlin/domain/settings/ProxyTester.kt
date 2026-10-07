package com.wynime.app.domain.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.get
import com.wynime.app.trace.ErrorReport
import com.wynime.datasources.bangumi.BangumiClientImpl
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.FlowRunning
import com.wynime.utils.coroutines.flows.restartable
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
private val isFirstTestResult = AtomicBoolean(true)

class ProxyTester(
    clientProvider: HttpClientProvider,
    flowScope: CoroutineScope,
    serviceIds: Set<String> = ServiceConnectionTesters.DefaultServiceIds,
) {
    private val proxyTestRunning = FlowRunning()
    private val proxyTestRestarter = FlowRestarter()

    private val connectionTester = clientProvider.configurationFlow.map {
        val client = clientProvider.get()

        ServiceConnectionTesters.createDefault(
            bangumiClient = BangumiClientImpl(client),
            serviceIds = serviceIds,
        )
    }
        .shareIn(
            flowScope,
            SharingStarted.WhileSubscribed(),
            replay = 1,
        )

    val testResult = connectionTester.flatMapLatest { it.results }
        .onEach {
            checkResultAndReport(it)
        }

    val testRunning = proxyTestRunning.isRunning

    suspend fun testRunnerLoop() {
        connectionTester
            .restartable(restarter = proxyTestRestarter)
            .collectLatest { tester ->
                proxyTestRunning.withRunning { tester.testAll() }
            }
    }

    fun restartTest() {
        proxyTestRestarter.restart()
    }
}

@OptIn(ExperimentalAtomicApi::class)
private fun checkResultAndReport(results: ServiceConnectionTester.Results) {
    if (results.anyFailed() && results.allCompleted()
        && isFirstTestResult.compareAndSet(expectedValue = true, newValue = false)
    ) {

        for ((service, state) in results.idToStateMap) {
            if (state is ServiceConnectionTester.TestState.Error) {

                ErrorReport.captureException(
                    ServiceTestUnknownErrorException(
                        message = "Service '$service' test failed with unknown exception",
                        cause = state.e,
                    ),
                )
                break
            }
        }

        reportNetworkCheckFailed(results)
    }
}

private fun reportNetworkCheckFailed(results: ServiceConnectionTester.Results) {
    Analytics.recordEvent(
        AnalyticsEvent.NetworkCheckFailed,
        results.idToStateMap.map { (id, state) ->
            "network_check_$id" to stateToString(state)
        }.toMap(),
    )
}

private fun stateToString(state: ServiceConnectionTester.TestState): String = when (state) {
    is ServiceConnectionTester.TestState.Error -> "error"
    ServiceConnectionTester.TestState.Failed -> "failed"
    ServiceConnectionTester.TestState.Idle -> "idle"
    is ServiceConnectionTester.TestState.Success -> "success"
    ServiceConnectionTester.TestState.Testing -> "testing"
}

private class ServiceTestUnknownErrorException(override val message: String?, override val cause: Throwable?) :
    Exception()
