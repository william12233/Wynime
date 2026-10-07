package com.wynime.app.platform.trace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.settings.ServiceConnectionTester
import com.wynime.app.domain.settings.ServiceConnectionTester.Service
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.platform.StartupTimeMonitor
import com.wynime.datasources.api.source.ConnectionStatus
import com.wynime.datasources.bangumi.BangumiClientImpl
import com.wynime.utils.analytics.AnalyticsEvent.Companion.AppStart
import com.wynime.utils.analytics.IAnalytics
import com.wynime.utils.analytics.recordEvent

suspend fun IAnalytics.recordAppStart(startupTimeMonitor: StartupTimeMonitor) {
    val client = GlobalKoin.get<HttpClientProvider>().get()

    val bangumiClient = BangumiClientImpl(client)

    val tester = ServiceConnectionTester(
        listOf(
            Service("bangumi") {
                bangumiClient.testConnectionMaster() == ConnectionStatus.SUCCESS
            },
            Service("bangumi_next") {
                bangumiClient.testConnectionNext() == ConnectionStatus.SUCCESS
            },
        ),
        Dispatchers.Default,
    )
    tester.testAll()
    val results = tester.results.first()

    recordEvent(AppStart) {
        putAll(startupTimeMonitor.getMarks())
        put("total_time", startupTimeMonitor.getTotalDuration().inWholeMilliseconds)

        results.idToStateMap.forEach { (key, value) ->
            put(
                "server_connectivity_$key",
                when (value) {
                    is ServiceConnectionTester.TestState.Error,
                    ServiceConnectionTester.TestState.Failed -> false

                    is ServiceConnectionTester.TestState.Success -> true

                    ServiceConnectionTester.TestState.Idle -> null
                    ServiceConnectionTester.TestState.Testing -> null
                },
            )
        }
    }
}
