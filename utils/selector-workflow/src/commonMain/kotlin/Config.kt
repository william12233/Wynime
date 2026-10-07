package com.wynime.utils.selectorworkflow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Immutable
data class SourceSpec(
    val name: String,
    val latency: Duration,
    val resultCount: Int,
    val candidates: Set<Int> = emptySet(),
    val priority: Boolean = false,
) {
    init {
        require(resultCount >= 0) { "resultCount must not be negative" }
        require(candidates.all { it in 0 until resultCount }) {
            "candidate index out of range for $name: $candidates, resultCount=$resultCount"
        }
        require(latency >= Duration.ZERO) { "latency must not be negative" }
    }
}

enum class SelectMode {

    WaitAll,

    Eager,
}

@Immutable
data class SelectionSpec(
    val mode: SelectMode = SelectMode.WaitAll,
    val priorityWait: Duration? = null,

    val demoBothPriorityPaths: Boolean = false,

    val lateLatency: Duration? = null,
) {
    init {
        require(priorityWait == null || priorityWait > Duration.ZERO) { "priorityWait must be positive" }
        require(lateLatency == null || lateLatency > Duration.ZERO) { "lateLatency must be positive" }
        if (demoBothPriorityPaths) {
            require(priorityWait != null) { "demoBothPriorityPaths requires priorityWait" }
        }
    }

    fun effectiveLateLatency(pacing: Pacing): Duration =
        lateLatency ?: pacing.unscaled(pacing.clockSweep * 1.5)
}

enum class ResolveOutcome {

    Hit,

    Timeout,

    HitAfterFallback,
}

@Immutable
data class ResolveSpec(
    val requestCount: Int = 8,
    val visibleRows: Int = 4,
    val hitRow: Int = 5,
    val budget: Duration = 8.seconds,
    val outcomes: List<ResolveOutcome> = listOf(ResolveOutcome.Hit),
) {
    init {
        require(requestCount > 0) { "requestCount must be positive" }
        require(visibleRows in 1..requestCount) { "visibleRows must be within 1..requestCount" }
        require(hitRow in 0 until requestCount) { "hitRow out of range" }
        require(budget > Duration.ZERO) { "budget must be positive" }
        require(outcomes.isNotEmpty()) { "outcomes must not be empty" }
        require(outcomes.first() != ResolveOutcome.HitAfterFallback) {
            "HitAfterFallback must follow a Timeout"
        }
    }
}

@Immutable
data class Pacing(

    val timeScale: Float = 0.3f,

    val clockSweep: Duration = 3.seconds + 500.milliseconds,

    val fade: Duration = 240.milliseconds,

    val cursorStep: Duration = 240.milliseconds,

    val cursorExit: Duration = 160.milliseconds,

    val pop: Duration = 400.milliseconds,

    val ripple: Duration = 560.milliseconds,

    val cacheDraw: Duration = 140.milliseconds,

    val handoff: Duration = 480.milliseconds,

    val windowOpenDelay: Duration = 190.milliseconds,

    val windowOpen: Duration = 240.milliseconds,

    val rowStagger: Duration = 240.milliseconds,

    val scroll: Duration = 800.milliseconds,

    val hold: Duration = 800.milliseconds,

    val finalHold: Duration = 1040.milliseconds,

    val reset: Duration = 320.milliseconds,

    val loopGap: Duration = 80.milliseconds,
) {
    init {
        require(timeScale > 0f) { "timeScale must be positive" }
        require(clockSweep > Duration.ZERO) { "clockSweep must be positive" }
    }

    fun scaled(demoTime: Duration): Duration = demoTime * timeScale.toDouble()

    fun unscaled(animationTime: Duration): Duration = animationTime / timeScale.toDouble()
}

@Immutable
data class SelectorWorkflowConfig(
    val sources: List<SourceSpec>,
    val selection: SelectionSpec = SelectionSpec(),
    val resolve: ResolveSpec = ResolveSpec(),
    val pacing: Pacing = Pacing(),

    val gridColumns: Int = 2,

    val cachedQuery: Boolean = false,

    val highlights: Set<HighlightRegion> = emptySet(),
) {
    init {
        require(sources.isNotEmpty()) { "at least one source is required" }
        require(gridColumns > 0) { "gridColumns must be positive" }
        require(sources.count { it.priority } <= 1) { "at most one priority source is supported" }
        if (selection.priorityWait != null) {
            require(sources.any { it.priority }) {
                "priorityWait is set but no source is marked as priority"
            }
        }
    }

    val results: List<ResultKey> = buildList {
        sources.forEachIndexed { s, spec ->
            repeat(spec.resultCount) { i -> add(ResultKey(s, i)) }
        }
    }

    val candidates: List<ResultKey> = results.filter { it.isCandidate(this) }

    val priorityIndex: Int? = sources.indexOfFirst { it.priority }.takeIf { it >= 0 }

    val showInterceptClock: Boolean = resolve.outcomes.any { it == ResolveOutcome.Timeout }

    val showPriorityMarks: Boolean = selection.priorityWait != null && priorityIndex != null

    val effectiveLatencies: List<Duration> =
        if (cachedQuery) sources.map { pacing.unscaled(pacing.cacheDraw) } else sources.map { it.latency }

    val featureHighlights: Set<HighlightRegion>
        get() = buildSet {
            if (cachedQuery) add(HighlightRegion.Sources)
            if (selection.mode == SelectMode.Eager || selection.priorityWait != null) {
                add(HighlightRegion.Results)
            }
            if (showInterceptClock) add(HighlightRegion.Resolve)
        }

    fun withFeatureHighlights(): SelectorWorkflowConfig = copy(highlights = featureHighlights)

    fun cellOf(key: ResultKey): Int = results.indexOf(key)

    val gridRows: Int = (results.size + gridColumns - 1) / gridColumns

    companion object
}

@Immutable
data class ResultKey(val source: Int, val indexInSource: Int) {
    fun isCandidate(config: SelectorWorkflowConfig): Boolean =
        indexInSource in config.sources[source].candidates
}

enum class HighlightRegion {

    Sources,

    Results,

    Resolve,
}

enum class ClockId {

    PriorityWait,

    InterceptBudget,
}

@Stable
object SelectorWorkflowPresets {

    fun threeSources(
        mode: SelectMode = SelectMode.WaitAll,
        priorityWait: Duration? = null,
        resolveOutcomes: List<ResolveOutcome> = listOf(ResolveOutcome.Hit),
        interceptBudget: Duration = 8.seconds,
        prioritySourceLatency: Duration = 7.seconds,
    ): SelectorWorkflowConfig = SelectorWorkflowConfig(
        sources = listOf(
            SourceSpec("源 A", latency = 2.seconds + 700.milliseconds, resultCount = 3),
            SourceSpec("源 B", latency = 3.seconds + 700.milliseconds, resultCount = 3, candidates = setOf(0)),
            SourceSpec(
                "源 C", latency = prioritySourceLatency, resultCount = 2,
                candidates = setOf(0), priority = true,
            ),
        ),
        selection = SelectionSpec(mode = mode, priorityWait = priorityWait),
        resolve = ResolveSpec(budget = interceptBudget, outcomes = resolveOutcomes),
    )
}
