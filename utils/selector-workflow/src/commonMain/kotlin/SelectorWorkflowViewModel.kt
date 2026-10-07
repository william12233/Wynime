package com.wynime.utils.selectorworkflow

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@Stable
class TimelinePlayer(
    timeline: SelectorWorkflowTimeline,
) {
    var timeline: SelectorWorkflowTimeline = timeline
        set(value) {
            field = value

            val ratio = if (state.duration > Duration.ZERO) {
                playhead.inWholeMicroseconds.toDouble() / state.duration.inWholeMicroseconds
            } else {
                0.0
            }
            playhead = value.duration * ratio.coerceIn(0.0, 1.0)
            state = value.sampleAt(playhead)
        }

    var playhead: Duration = Duration.ZERO
        private set

    var state: SelectorWorkflowState by mutableStateOf(timeline.sampleAt(Duration.ZERO))
        private set

    var isPlaying: Boolean by mutableStateOf(true)

    var speed: Float by mutableStateOf(1f)

    var loop: Boolean by mutableStateOf(true)

    private var lastFrameNanos: Long = UNSET

    fun onFrame(frameTimeNanos: Long) {
        val previous = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (previous == UNSET) return
        if (!isPlaying) return
        val delta = (frameTimeNanos - previous).coerceIn(0L, MAX_FRAME_NANOS)
        advance(delta.nanoseconds * speed.toDouble())
    }

    fun advance(by: Duration) {
        if (by <= Duration.ZERO) return
        val total = timeline.duration
        var next = playhead + by
        if (next > total) {
            next = if (loop && total > Duration.ZERO) {
                (next.inWholeMicroseconds % total.inWholeMicroseconds).microsecondsDuration()
            } else {
                total
            }
        }
        seekTo(next)
    }

    fun seekTo(time: Duration) {
        playhead = time.coerceIn(Duration.ZERO, timeline.duration)
        state = timeline.sampleAt(playhead)
    }

    fun seekToFraction(fraction: Float) {
        seekTo(timeline.duration * fraction.coerceIn(0f, 1f).toDouble())
    }

    fun restart() {
        lastFrameNanos = UNSET
        seekTo(Duration.ZERO)
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE

        const val MAX_FRAME_NANOS = 100_000_000L
    }
}

private fun Long.microsecondsDuration(): Duration = (this * 1000).nanoseconds

@Stable
class SelectorWorkflowViewModel(
    initialConfig: SelectorWorkflowConfig = SelectorWorkflowPresets.threeSources(),
) : ViewModel() {

    var config: SelectorWorkflowConfig by mutableStateOf(initialConfig)
        private set

    val player: TimelinePlayer = TimelinePlayer(initialConfig.buildTimeline())

    val state: SelectorWorkflowState get() = player.state

    fun onFrame(frameTimeNanos: Long) = player.onFrame(frameTimeNanos)

    fun updateConfig(transform: (SelectorWorkflowConfig) -> SelectorWorkflowConfig): Boolean {
        val next = runCatching { transform(config) }.getOrNull() ?: return false
        if (next == config) return true
        val timeline = runCatching { next.buildTimeline() }.getOrNull() ?: return false
        config = next
        player.timeline = timeline
        return true
    }

    fun configure(
        eager: Boolean,
        priorityWaitSeconds: Int? = null,
        resolveBudgetSeconds: Int? = null,
        cacheQuery: Boolean = false,
        highlight: HighlightRegion? = null,
        restart: Boolean = true,
    ): Boolean {
        val ok = updateConfig { current ->
            current.copy(
                cachedQuery = cacheQuery,
                highlights = setOfNotNull(highlight),
                selection = current.selection.copy(
                    mode = if (eager) SelectMode.Eager else SelectMode.WaitAll,
                    priorityWait = priorityWaitSeconds?.seconds,
                    demoBothPriorityPaths = priorityWaitSeconds != null,
                    lateLatency = null,
                ),
                resolve = current.resolve.copy(
                    budget = (resolveBudgetSeconds ?: current.resolve.budget.inWholeSeconds.toInt())
                        .coerceAtLeast(1).seconds,
                    outcomes = if (resolveBudgetSeconds != null) {
                        listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback)
                    } else {
                        listOf(ResolveOutcome.Hit)
                    },
                ),
            )
        }
        if (ok && restart) player.restart()
        return ok
    }

    fun setEagerSelect(enabled: Boolean) = updateConfig {
        it.copy(
            selection = it.selection.copy(
                mode = if (enabled) SelectMode.Eager else SelectMode.WaitAll,
            ),
        ).withFeatureHighlights()
    }

    fun setPriorityWait(enabled: Boolean, seconds: Int = DEFAULT_PRIORITY_WAIT_SECONDS) = updateConfig {
        it.copy(
            selection = it.selection.copy(
                priorityWait = if (enabled) seconds.seconds else null,
                demoBothPriorityPaths = enabled,
                lateLatency = null,
            ),
        ).withFeatureHighlights()
    }

    fun setPriorityWaitSeconds(seconds: Int) = updateConfig {
        if (it.selection.priorityWait == null) it
        else it.copy(selection = it.selection.copy(priorityWait = seconds.seconds))
    }

    fun setCacheQuery(enabled: Boolean) = updateConfig {
        it.copy(cachedQuery = enabled).withFeatureHighlights()
    }

    fun setResolveDemo(enabled: Boolean) = updateConfig {
        it.copy(
            resolve = it.resolve.copy(
                outcomes = if (enabled) {
                    listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback)
                } else {
                    listOf(ResolveOutcome.Hit)
                },
            ),
        ).withFeatureHighlights()
    }

    fun setInterceptBudgetSeconds(seconds: Int) = updateConfig {
        it.copy(resolve = it.resolve.copy(budget = seconds.seconds))
    }

    private companion object {
        const val DEFAULT_PRIORITY_WAIT_SECONDS = 5
    }
}
