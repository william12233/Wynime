package com.wynime.utils.coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun <T> Flow<T>.retryWithBackoffDelay(
    maxAttempts: Long,
    predicate: suspend FlowCollector<T>.(cause: Throwable, attempt: Long) -> Boolean = { _, _ -> true },
): Flow<T> {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    return this.retryWhen { cause, attempt ->
        if (attempt >= maxAttempts) {
            throw cause
        }
        if (predicate(cause, attempt)) {
            delay(backoffDelay(attempt.toInt()))
            true
        } else {
            false
        }
    }
}

fun <T> Flow<T>.retryWithBackoffDelay(
    predicate: suspend FlowCollector<T>.(cause: Throwable, attempt: Long) -> Boolean = { _, _ -> true },
): Flow<T> {
    return this.retryWhen { cause, attempt ->
        if (predicate(cause, attempt)) {
            delay(backoffDelay(attempt.toInt()))
            true
        } else {
            false
        }
    }
}

internal fun backoffDelay(failureCount: Int): Duration {
    return when (failureCount) {
        0, 1 -> 1.seconds
        2 -> 2.seconds
        3 -> 4.seconds
        4 -> 8.seconds
        5 -> 16.seconds
        else -> 30.seconds
    }
}

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
inline fun CancellationException(
    message: String? = null,
    cause: Throwable? = null
): kotlinx.coroutines.CancellationException {
    return kotlinx.coroutines.CancellationException(
        message = message,
        cause,
    )
}
