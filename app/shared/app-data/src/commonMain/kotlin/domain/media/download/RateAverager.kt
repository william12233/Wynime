package com.wynime.app.domain.media.download

import kotlinx.atomicfu.AtomicLongArray
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.select
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

private class RateAverager(
    private val bytes: ReceiveChannel<Long>,
    private val ticker: ReceiveChannel<Unit>,
    windowSize: Int = 5,
) {
    init {
        require(windowSize > 0) { "windowSize must be greater than 0" }
    }

    private val window = AtomicLongArray(windowSize)

    @Volatile
    private var currentIndex = -1

    @Volatile
    private var counted = 0u

    @Volatile
    private var latestValue = -1L

    @Volatile
    private var missingValuePassCount = 0

    suspend fun runPass(): Long? = select {
        bytes.onReceive {
            latestValue = it
            missingValuePassCount = 0
            null
        }
        ticker.onReceive {
            val latestValue = latestValue
            if (latestValue != -1L) {
                pushValueToWindow(latestValue)
                this@RateAverager.latestValue = -1L
            } else {
                if (currentIndex == -1) return@onReceive 0
                missingValuePassCount += 1
                if (missingValuePassCount >= 2) {
                    pushValueToWindow(window[currentIndex].value)
                }
            }
            check(currentIndex != -1)
            if (counted == 1u) return@onReceive 0

            val windowSize = window.size
            val toCompareIndex = if (counted < windowSize.toUInt()) {
                0
            } else {
                (currentIndex + 1) % windowSize
            }
            if (counted == 0u) {
                0
            } else {
                val current = window[currentIndex].value
                val toCompare = window[toCompareIndex].value
                (current - toCompare).coerceAtLeast(0L) / counted.coerceAtMost(windowSize.toUInt()).toLong()
            }
        }
    }

    private fun pushValueToWindow(value: Long) {
        currentIndex = (currentIndex + 1) % window.size
        window[currentIndex].value = value
        counted++
    }
}

internal fun Flow<Long>.averageRate(
    windowSize: Int = 5,
    tickerFlow: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1.seconds)
        }
    },
): Flow<Long> = flow {
    coroutineScope {
        RateAverager(produceIn(this), tickerFlow.produceIn(this), windowSize).run {
            while (true) {
                runPass()?.let { emit(it) }
            }
        }
    }
}
