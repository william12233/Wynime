package com.wynime.utils.coroutines

import kotlin.coroutines.CoroutineContext

actual suspend fun <R> runInterruptible(context: CoroutineContext, block: () -> R): R {
    return kotlinx.coroutines.runInterruptible(context, block)
}