package com.wynime.utils.selectorworkflow

import kotlin.time.Duration

internal data class PlannedCursor(
    val id: String,
    val owner: Int?,
    val stops: List<CursorStop>,
    val exitAt: Duration,
    val cancelled: Boolean,
    val peakAlpha: Float = 1f,
)

internal data class CursorStop(val time: Duration, val cell: Int)

internal data class SelectionPlan(
    val cursors: List<PlannedCursor>,
    val winner: ResultKey?,
    val winnerAt: Duration,
)

internal object SelectionEngine {

    fun plan(
        config: SelectorWorkflowConfig,
        mode: SelectMode,
        ready: List<Duration?>,
        step: Duration,
        stagger: Duration,
        idPrefix: String = "",
    ): SelectionPlan {
        val participating = config.sources.indices.filter { ready[it] != null }
        if (participating.isEmpty()) return SelectionPlan(emptyList(), null, Duration.ZERO)

        val raw: List<RawCursor> = when (mode) {
            SelectMode.WaitAll -> {

                val start = participating.maxOf { ready[it]!! }
                val cells = config.results
                    .withIndex()
                    .filter { (_, key) -> key.source in participating }
                    .map { (cell, _) -> cell }
                listOf(RawCursor("${idPrefix}global", owner = null, start = start, cells = cells))
            }

            SelectMode.Eager -> {

                val order = participating.sortedBy { ready[it]!! }
                var previous: Duration? = null
                var sameInstantRank = 0
                order.map { source ->
                    val at = ready[source]!!
                    if (at == previous) sameInstantRank++ else sameInstantRank = 0
                    previous = at
                    RawCursor(
                        id = "${idPrefix}src$source",
                        owner = source,
                        start = at + stagger * sameInstantRank.toDouble(),
                        cells = config.results.withIndex()
                            .filter { (_, key) -> key.source == source }
                            .map { (cell, _) -> cell },
                    )
                }
            }
        }

        val arrivals = raw.map { cursor ->
            cursor.cells.mapIndexed { i, cell -> CursorStop(cursor.start + step * i.toDouble(), cell) }
        }

        var winner: ResultKey? = null
        var winnerAt = Duration.ZERO
        arrivals.flatten()
            .filter { config.results[it.cell].isCandidate(config) }
            .minWithOrNull(compareBy({ it.time }, { it.cell }))
            ?.let {
                winner = config.results[it.cell]
                winnerAt = it.time
            }

        val decidedAt = winner?.let { winnerAt }
        val planned = raw.mapIndexed { index, cursor ->
            val stops = arrivals[index]
            val kept = if (decidedAt == null) stops else stops.filter { it.time <= decidedAt }
            val effective = kept.ifEmpty { stops.take(1) }
            val naturalEnd = effective.last().time + step
            val cancelled = decidedAt != null && naturalEnd > decidedAt && effective.size < stops.size
            PlannedCursor(
                id = cursor.id,
                owner = cursor.owner,
                stops = effective,
                exitAt = if (decidedAt != null) minOf(naturalEnd, maxOf(decidedAt, effective.last().time))
                else naturalEnd,
                cancelled = cancelled,
            )
        }.filter { it.stops.isNotEmpty() && (decidedAt == null || it.stops.first().time <= decidedAt) }

        return SelectionPlan(planned, winner, winnerAt)
    }

    fun planPriorityHit(
        config: SelectorWorkflowConfig,
        priorityIndex: Int,
        ready: Duration,
        step: Duration,
        idPrefix: String,
    ): SelectionPlan {
        val cells = config.results.withIndex()
            .filter { (_, key) -> key.source == priorityIndex }
            .map { (cell, _) -> cell }
        val stops = cells.mapIndexed { i, cell -> CursorStop(ready + step * i.toDouble(), cell) }
        val hit = stops.firstOrNull { config.results[it.cell].isCandidate(config) }
        val kept = if (hit == null) stops else stops.filter { it.time <= hit.time }
        return SelectionPlan(
            cursors = listOf(
                PlannedCursor(
                    id = "${idPrefix}prio",
                    owner = priorityIndex,
                    stops = kept,
                    exitAt = kept.last().time,
                    cancelled = false,
                ),
            ),
            winner = hit?.let { config.results[it.cell] },
            winnerAt = hit?.time ?: (stops.lastOrNull()?.time ?: ready),
        )
    }

    fun nextCandidateAfter(config: SelectorWorkflowConfig, current: ResultKey): ResultKey? {
        val all = config.candidates
        if (all.size <= 1) return null
        val i = all.indexOf(current)
        if (i < 0) return all.firstOrNull()
        return all[(i + 1) % all.size]
    }

    private data class RawCursor(
        val id: String,
        val owner: Int?,
        val start: Duration,
        val cells: List<Int>,
    )
}
