package com.wynime.utils.coroutines.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FlowRestarter {
    internal val id = MutableStateFlow(0)

    fun restart() {
        id.update { it + 1 }
    }
}

fun <T> Flow<T>.restartable(restarter: FlowRestarter): Flow<T> = restarter.id.flatMapLatest { this }

class FlowRunning {
    @Suppress("PropertyName")
    @PublishedApi
    internal val _isRunning: MutableStateFlow<Int> = MutableStateFlow<Int>(0)

    val isRunning = _isRunning.map { it > 0 }

    inline fun <R> withRunning(block: () -> R): R {
        _isRunning.update { it + 1 }
        try {
            return block()
        } finally {
            _isRunning.update { it - 1 }
        }
    }
}

