package com.wynime.utils.coroutines.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

suspend inline fun <T, R> FlowCollector<List<T>>.runOrEmitEmptyList(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    try {
        return block()
    } catch (e: Throwable) {
        emit(emptyList())
        throw e
    }
}

fun <T> Flow<T>.catching(): Flow<Result<T>> = map {
    Result.success(it)
}.catch {
    if (it is CancellationException) {
        throw it
    }

    emit(Result.failure(it))
}

fun <T> Flow<T>.shareTransparentlyIn(
    scope: CoroutineScope,
    started: SharingStarted,
    replay: Int = 0,
) = this
    .catching()
    .shareIn(
        scope, started, replay,
    )
    .map {
        it.getOrThrow()
    }
