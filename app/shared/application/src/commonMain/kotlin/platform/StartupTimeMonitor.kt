package com.wynime.app.platform

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.TimeSource

class StartupTimeMonitor(
    private val start: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
) {
    private class Mark(
        val step: String,
        val time: Duration,
        val duration: Duration,
    )

    private val marks = ArrayList<Mark>(10)

    fun mark(stepDone: StepName) {
        mark(stepDone.name)
    }

    private fun mark(stepDone: String) {
        val time = start.elapsedNow()
        val duration = if (marks.isEmpty()) {
            time
        } else {
            time - marks.last().time
        }
        marks.add(Mark(stepDone, time, duration))
    }

    fun getMarks(): Map<String, Long> {
        return buildMap {
            for (mark in marks) {
                put("time_" + mark.step, mark.duration.inWholeMilliseconds)
            }
        }
    }

    fun getTotalDuration(): Duration {
        return if (marks.isEmpty()) {
            Duration.ZERO
        } else {
            marks.sumOf { it.duration }
        }
    }
}

@JvmInline
value class StepName(
    val name: String,
) {
    override fun toString(): String = name

    companion object {
        val UncaughtExceptionHandler = StepName("UncaughtExceptionHandler")
        val ProjectDirectories = StepName("ProjectDirectories")
        val Logging = StepName("Logging")
        val WindowAndContext = StepName("WindowAndContext")
        val SingletonChecker = StepName("SingletonChecker")
        val Modules = StepName("Modules")
        val LaunchAsyncInitializers = StepName("LaunchAsyncInitializers")
        val PagingHack = StepName("PagingHack")
        val Analytics = StepName("Analytics")
        val ThemeDetector = StepName("ThemeDetector")
    }
}

private inline fun <T> Iterable<T>.sumOf(selector: (T) -> Duration): Duration {
    var sum: Duration = Duration.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
