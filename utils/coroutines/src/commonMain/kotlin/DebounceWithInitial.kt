package com.wynime.utils.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OverloadResolutionByLambdaReturnType
@JvmName("debounceWithInitialDuration")
fun <T> Flow<T>.debounceWithInitial(
    timeout: () -> Duration,
): Flow<T> {
    val isInitial = object {
        @Volatile
        var value = true
    }
    return debounce {
        if (isInitial.value) {
            isInitial.value =  false
            Duration.ZERO
        } else {
            timeout()
        }
    }
}

@OverloadResolutionByLambdaReturnType
fun <T> Flow<T>.debounceWithInitial(timeoutMillis: () -> Long): Flow<T> =
    debounceWithInitial { timeoutMillis().milliseconds }

fun <T> Flow<T>.debounceWithInitial(timeout: Duration): Flow<T> =
    debounceWithInitial { timeout }

fun <T> Flow<T>.debounceWithInitial(timeoutMillis: Long): Flow<T> =
    debounceWithInitial { timeoutMillis.milliseconds }
