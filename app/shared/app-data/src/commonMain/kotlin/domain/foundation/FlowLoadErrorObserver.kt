package com.wynime.app.domain.foundation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

sealed interface FlowLoadErrorObserver {

    val loadErrorState: StateFlow<LoadError?>
}

fun FlowLoadErrorObserver(): FlowLoadErrorObserver = FlowLoadErrorObserverImpl()

fun <T> Flow<T>.catchLoadError(observer: FlowLoadErrorObserver): Flow<T> {

    when (observer) {
        is FlowLoadErrorObserverImpl -> {}
    }

    return this
        .onEach {

            observer.loadErrorStateMutable.value = null
        }
        .onCompletion { exception ->

            if (exception != null && exception !is CancellationException) {
                observer.loadErrorStateMutable.value = LoadError.fromException(exception)
            }
            if (exception is CancellationException) {
                throw exception
            }
        }
}

private class FlowLoadErrorObserverImpl : FlowLoadErrorObserver {
    val loadErrorStateMutable: MutableStateFlow<LoadError?> = MutableStateFlow(null)
    override val loadErrorState: StateFlow<LoadError?> = loadErrorStateMutable.asStateFlow()
}
