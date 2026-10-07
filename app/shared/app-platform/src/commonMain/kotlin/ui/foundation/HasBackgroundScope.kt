package com.wynime.app.ui.foundation

import androidx.annotation.UiThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.tools.MonoTasker
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

@Stable
interface HasBackgroundScope {

    val backgroundScope: CoroutineScope

    fun <T> Flow<T>.shareInBackground(
        started: SharingStarted = SharingStarted.WhileSubscribed(5.seconds),
        replay: Int = 1,
    ): SharedFlow<T> = shareIn(backgroundScope, started, replay)

    fun <T> Flow<T>.stateInBackground(
        initialValue: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(5.seconds),
    ): StateFlow<T> = stateIn(backgroundScope, started, initialValue)

    fun <T> Flow<T>.stateInBackground(
        started: SharingStarted = SharingStarted.WhileSubscribed(5.seconds),
    ): StateFlow<T?> = stateIn(backgroundScope, started, null)

    private val <T> Flow<T>.valueOrNull: T?
        get() = when (this) {
            is StateFlow<T> -> this.value
            is SharedFlow<T> -> this.replayCache.firstOrNull()
            else -> null
        }

    fun <T> Flow<T>.produceState(
        initialValue: T,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): State<T> {
        val state = mutableStateOf(valueOrNull ?: initialValue)
        launchInBackground(coroutineContext) {
            flowOn(Dispatchers.Default)
                .collect { value ->
                    withContext(Dispatchers.Main) {
                        state.value = value
                    }
                }
        }
        return state
    }

    fun Flow<Float>.produceState(
        initialValue: Float,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): FloatState {
        val state = mutableFloatStateOf(this.valueOrNull ?: initialValue)
        launchInBackground(coroutineContext) {
            flowOn(Dispatchers.Default)
                .collect {
                    withContext(Dispatchers.Main) {
                        state.value = it
                    }
                }
        }
        return state
    }

    fun Flow<Int>.produceState(
        initialValue: Int,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): IntState {
        val state = mutableIntStateOf(this.valueOrNull ?: initialValue)
        launchInBackground(coroutineContext) {
            flowOn(Dispatchers.Default)
                .collect {
                    withContext(Dispatchers.Main) {
                        state.value = it
                    }
                }
        }
        return state
    }

    fun Flow<Long>.produceState(
        initialValue: Long,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): LongState {
        val state = mutableLongStateOf(this.valueOrNull ?: initialValue)
        launchInBackground(coroutineContext) {
            flowOn(Dispatchers.Default)
                .collect {
                    withContext(Dispatchers.Main) {
                        state.value = it
                    }
                }
        }
        return state
    }

    fun <T> StateFlow<T>.produceState(
        initialValue: T = this.value,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
    ): State<T> {
        val state = mutableStateOf(initialValue)
        launchInBackground(coroutineContext) {

            collect {
                withContext(Dispatchers.Main) {
                    state.value = it
                }
            }
        }
        return state
    }
}

@Suppress("FunctionName")
fun BackgroundScope(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext
): HasBackgroundScope = SimpleBackgroundScope(parentCoroutineContext)

@Composable
inline fun rememberBackgroundScope(
    crossinline coroutineContext: @DisallowComposableCalls () -> CoroutineContext = { EmptyCoroutineContext }
): HasBackgroundScope = remember { RememberedBackgroundScope(coroutineContext()) }

private class SimpleBackgroundScope(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext
) : HasBackgroundScope {
    override val backgroundScope: CoroutineScope =
        CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))
}

@PublishedApi
internal class RememberedBackgroundScope(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext
) : HasBackgroundScope, RememberObserver {
    private companion object {
        private val logger = logger<RememberedBackgroundScope>()
    }

    private val creationStacktrace =
        if (currentWynimeBuildConfig.isDebug) Throwable("Stacktrace for background scope creation") else null

    override val backgroundScope: CoroutineScope =
        CoroutineScope(
            CoroutineExceptionHandler { coroutineContext, throwable ->
                if (throwable is CancellationException) return@CoroutineExceptionHandler
                creationStacktrace?.let { throwable.addSuppressed(it) }
                logger.error(throwable) { "An error occurred in the background scope in coroutine $coroutineContext" }
            }.plus(parentCoroutineContext)
                .plus(SupervisorJob(parentCoroutineContext[Job])),
        )

    override fun onAbandoned() {
        backgroundScope.cancel("RememberedBackgroundScope left the composition")
    }

    override fun onForgotten() {
        backgroundScope.cancel("RememberedBackgroundScope left the composition")
    }

    override fun onRemembered() {
    }
}

fun <V : HasBackgroundScope> V.launchInBackgroundAnimated(
    isLoadingState: MutableState<Boolean>,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend V.() -> Unit,
): Job {
    isLoadingState.value = true
    return backgroundScope.launch(context, start) {
        block()
        isLoadingState.value = false
    }
}

fun <T> CoroutineScope.deferFlow(value: suspend () -> T): MutableStateFlow<T?> {
    val flow = MutableStateFlow<T?>(null)
    launch {
        flow.value = value()
    }
    return flow
}

fun <V : HasBackgroundScope> V.launchInBackground(
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend V.() -> Unit,
): Job {
    return backgroundScope.launch(start = start) {
        block()
    }
}

fun <V : HasBackgroundScope> V.launchInBackground(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend V.() -> Unit,
): Job {
    return backgroundScope.launch(context, start) {
        block()
    }
}

fun <V : HasBackgroundScope> V.launchInMain(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    @UiThread block: suspend V.() -> Unit,
): Job {
    return backgroundScope.launch(context + Dispatchers.Main, start) {
        block()
    }
}

fun <T> Flow<T>.produceState(
    initialValue: T,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    val state = mutableStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        flowOn(Dispatchers.Default)
            .collect {

                state.value = it
            }
    }
    return state
}

fun Flow<Float>.produceState(
    initialValue: Float,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): FloatState {
    val state = mutableFloatStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        flowOn(Dispatchers.Default)
            .collect {

                state.value = it
            }
    }
    return state
}

fun Flow<Int>.produceState(
    initialValue: Int,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): IntState {
    val state = mutableIntStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        flowOn(Dispatchers.Default)
            .collect {

                state.value = it
            }
    }
    return state
}

fun Flow<Long>.produceState(
    initialValue: Long,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): LongState {
    val state = mutableLongStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        flowOn(Dispatchers.Default)
            .collect {

                state.value = it
            }
    }
    return state
}

fun <T> StateFlow<T>.produceState(
    initialValue: T = this.value,
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    val state = mutableStateOf(initialValue)
    scope.launch(coroutineContext + Dispatchers.Main) {
        collect {

            state.value = it
        }
    }
    return state
}

@Composable
inline fun HasBackgroundScope.rememberBackgroundMonoTasker(
    crossinline getContext: @DisallowComposableCalls () -> CoroutineContext = { EmptyCoroutineContext }
): MonoTasker {
    val tasker = remember(this) { MonoTasker(backgroundScope) }
    return tasker
}