package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@Stable
interface AsyncHandler {

    val isWorking: Boolean

    fun launch(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job

    fun cancelLast()
}

@Composable
fun rememberAsyncHandler(): AsyncHandler {
    val toaster by rememberUpdatedState(LocalToaster.current)
    return rememberAsyncHandler(
        onException = { e ->
            toaster.showLoadError(LoadError.fromException(e))
        },
    )
}

@Composable
fun rememberAsyncHandler(
    onException: (Throwable) -> Unit,
): AsyncHandler {
    val scope = rememberCoroutineScope()
    val onExceptionState = rememberUpdatedState(onException)
    val handler = remember(scope, onExceptionState) {
        object : AsyncHandler {
            private val workingTaskCount = mutableIntStateOf(0)
            override val isWorking: Boolean get() = workingTaskCount.intValue > 0
            private var lastJob: Job? = null

            override fun launch(
                coroutineContext: CoroutineContext,
                block: suspend CoroutineScope.() -> Unit,
            ): Job {
                coroutineContext[ContinuationInterceptor]?.let {
                    if (it !== Dispatchers.Main.immediate) {
                        throw IllegalArgumentException("ContinuationInterceptor for AsyncHandler.launch must be Dispatchers.Main.immediate, but was: $it")
                    }
                }

                workingTaskCount.intValue++

                return scope.launch(
                    coroutineContext + Dispatchers.Main.immediate,
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    val myJob = this.coroutineContext.job
                    lastJob = myJob
                    try {
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        onExceptionState.value(e)
                    } finally {
                        workingTaskCount.intValue--
                        if (lastJob === myJob) {
                            lastJob = null
                        }
                    }
                }
            }

            override fun cancelLast() {
                lastJob?.cancel()
            }
        }
    }
    return handler
}
