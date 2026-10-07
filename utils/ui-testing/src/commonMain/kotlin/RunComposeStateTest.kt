package com.wynime.app.ui.framework

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun runComposeStateTest(
    context: CoroutineContext = EmptyCoroutineContext,
    testBody: suspend TestScope.() -> Unit
) = runTest(context) {
    setDispatcher()
    try {
        testBody()
    } finally {
        Dispatchers.resetMain()
    }
}

fun runComposeStateTest(
    context: CoroutineContext = EmptyCoroutineContext,
    timeout: Duration = 60.seconds,
    testBody: suspend TestScope.() -> Unit
) = runTest(context, timeout) {
    setDispatcher()
    try {
        testBody()
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalStdlibApi::class)
private suspend inline fun setDispatcher() {
    Dispatchers.setMain(currentCoroutineContext()[CoroutineDispatcher]!!)
}

suspend fun TestScope.takeSnapshot() {

    yield()
    testScheduler.runCurrent()
    Snapshot.sendApplyNotifications()
}
