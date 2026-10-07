package com.wynime.utils.selectorworkflow

import kotlin.time.Duration

fun SelectorWorkflowConfig.buildTimeline(): SelectorWorkflowTimeline {
    require(!showInterceptClock || interceptStopFraction() < 1f) {
        "pacing.clockSweep (${pacing.clockSweep}) is too short: the intercept would time out before " +
                "the request list finishes streaming (needs > ${interceptElapsed()}). " +
                "Use clockSweepForInterceptStop() to size it."
    }
    val storyboard = Storyboard(this)
    val passes = buildPasses()
    passes.forEachIndexed { index, pass ->
        storyboard.playPass(pass, isLast = index == passes.lastIndex)
    }
    return storyboard.build()
}

fun SelectorWorkflowConfig.interceptElapsed(): Duration {
    val p = pacing
    val rowsIn = p.rowStagger * resolve.visibleRows.toDouble()
    return p.windowOpen + rowsIn + p.scroll + p.fade
}

fun SelectorWorkflowConfig.interceptStopFraction(): Float {
    val full = pacing.clockSweep
    if (full <= Duration.ZERO) return 1f
    return (interceptElapsed().inWholeMicroseconds.toFloat() / full.inWholeMicroseconds).coerceIn(0f, 1f)
}

fun SelectorWorkflowConfig.clockSweepForInterceptStop(fraction: Float): Duration {
    require(fraction in 0.05f..1f) { "fraction must be in 0.05..1" }
    return interceptElapsed() / fraction.toDouble()
}

internal data class Pass(

    val latencies: List<Duration>,

    val gate: Gate?,
) {
    enum class Gate { Hit, Timeout }
}

internal fun SelectorWorkflowConfig.buildPasses(): List<Pass> {
    val base = effectiveLatencies
    if (selection.priorityWait == null) return listOf(Pass(base, gate = null))
    val prio = checkNotNull(priorityIndex) { "priorityWait requires a priority source" }

    val gateWindow = pacing.clockSweep

    if (!selection.demoBothPriorityPaths) {
        val gate = if (pacing.scaled(base[prio]) <= gateWindow) Pass.Gate.Hit else Pass.Gate.Timeout
        return listOf(Pass(base, gate))
    }

    val fast = base.toMutableList().also { it[prio] = pacing.unscaled(gateWindow * PRIORITY_HIT_FRACTION) }
    val slow = base.toMutableList().also { it[prio] = selection.effectiveLateLatency(pacing) }
    return listOf(Pass(fast, Pass.Gate.Hit), Pass(slow, Pass.Gate.Timeout))
}

private const val PRIORITY_HIT_FRACTION = 0.7

private fun Storyboard.playPass(pass: Pass, isLast: Boolean) {
    val p = config.pacing
    val passStart = now

    val finish = pass.latencies.map { passStart + p.scaled(it) }

    val prio = config.priorityIndex
    val gateStart = passStart
    val gateStop: Duration?
    val plan: SelectionPlan

    when (pass.gate) {
        null -> {
            gateStop = null
            plan = SelectionEngine.plan(
                config = config,
                mode = config.selection.mode,
                ready = finish,
                step = p.cursorStep,
                stagger = p.cursorStep * 1.5,
            )
        }

        Pass.Gate.Hit -> {

            val index = checkNotNull(prio)
            gateStop = finish[index]
            plan = SelectionEngine.planPriorityHit(config, index, finish[index], p.cursorStep, idPrefix = "")
        }

        Pass.Gate.Timeout -> {

            val open = passStart + p.clockSweep
            gateStop = open
            plan = SelectionEngine.plan(
                config = config,
                mode = config.selection.mode,
                ready = finish.map { if (it <= open) open else null },
                step = p.cursorStep,
                stagger = p.cursorStep * 1.5,
            )
        }
    }

    if (pass.gate != null) {
        val clock = clocks.getValue(ClockId.PriorityWait)
        at(gateStart) { clock.start() }
        at(checkNotNull(gateStop)) {
            phase(if (pass.gate == Pass.Gate.Hit) "priority-hit" else "priority-timeout")
            if (pass.gate == Pass.Gate.Hit) clock.stop() else clock.expire()
        }
    }

    val decidedAt = plan.winner?.let { plan.winnerAt }
    at(passStart) { phase(if (config.cachedQuery) "search-cached" else "search") }
    config.sources.indices.forEach { i ->
        at(passStart) {

            if (config.cachedQuery) linkOf(i).markCached() else sources[i].beginSearch()
            linkOf(i).draw(over = p.scaled(pass.latencies[i]))
        }
        val late = decidedAt != null && finish[i] >= decidedAt
        at(finish[i]) {
            sources[i].settle()
            linkOf(i).mute()

            if (config.cachedQuery) sourceRipple(i).pulse()
            chipsOf(i).forEach { it.appear(target = if (late) Storyboard.MUTED_ALPHA else 1f) }
        }
    }

    plan.cursors.forEach { planned ->
        val handle = cursor(planned.id, planned.owner)
        at(planned.stops.first().time) { handle.enter(planned.stops.first().cell, planned.peakAlpha) }
        planned.stops.drop(1).forEach { stop ->
            at(stop.time - p.cursorStep) { handle.step(stop.cell, p.cursorStep) }
        }
        at(planned.exitAt) { handle.leave() }
    }

    val winner = plan.winner
    if (winner == null) {

        seekAfter(finish.max() + p.hold)
        if (!isLast) resetPass(p.reset) else finalHold(p)
        return
    }

    var selected: ResultKey = winner
    at(plan.winnerAt) {
        phase("select")
        val chosen = selected
        chipOf(chosen).select()
        rippleAt(config.cellOf(chosen)).pulse()

        chips.filter { it.key != chosen && finish[it.key.source] < plan.winnerAt }
            .forEach { it.mute() }
    }
    seekAfter(plan.winnerAt + config.pacing.pop)

    config.resolve.outcomes.forEachIndexed { index, outcome ->
        if (outcome == ResolveOutcome.HitAfterFallback) {
            selected = fallbackToNextCandidate(selected) ?: selected
        }
        playResolve(
            outcome = outcome,
            selected = selected,
            handoffNeeded = index == 0 || outcome == ResolveOutcome.HitAfterFallback,
            isLastOfPass = index == config.resolve.outcomes.lastIndex,
        )
    }

    if (!isLast) resetPass(p.reset) else finalHold(p)
}

private fun Storyboard.fallbackToNextCandidate(current: ResultKey): ResultKey? {
    val p = config.pacing
    val next = SelectionEngine.nextCandidateAfter(config, current) ?: return null
    phase("fallback")
    chipOf(current).fail()
    handoff.retract(p.fade)
    advance(p.fade * 1.6)

    val handle = cursor("fallback", next.source)
    handle.enter(config.cellOf(current))
    advance(p.cursorStep * 0.7)
    handle.step(config.cellOf(next), p.cursorStep)
    advance(p.cursorStep)

    chipOf(current).mute()
    chipOf(next).select()
    rippleAt(config.cellOf(next)).pulse()
    handle.leave()
    advance(p.pop)
    return next
}

private fun Storyboard.playResolve(
    outcome: ResolveOutcome,
    selected: ResultKey,
    handoffNeeded: Boolean,
    isLastOfPass: Boolean,
) {
    val p = config.pacing
    val clock = clocks.getValue(ClockId.InterceptBudget)
    val showClock = config.showInterceptClock

    if (handoffNeeded) {
        phase("handoff")
        handoff.draw(over = p.handoff)
        advance(p.handoff + p.windowOpenDelay)
        window.open()
    }
    val clockStart = now
    if (showClock) clock.start()
    advance(p.windowOpen)

    phase(if (outcome == ResolveOutcome.Timeout) "resolve-timeout" else "resolve-hit")
    val timeoutAt = if (showClock) clockStart + clock.fullSweepDuration else now
    requestList.stream(
        mediaRowIcon = if (outcome == ResolveOutcome.Timeout) RequestIcon.Request else RequestIcon.Media,
    )
    advance(p.rowStagger * config.resolve.visibleRows.toDouble())
    requestList.scrollTo(config.resolve.hitRow)
    advance(p.scroll)

    when (outcome) {
        ResolveOutcome.Timeout -> {

            seekAfter(timeoutAt)
            clock.expire()
            window.markFailed()
            advance(p.fade)
        }

        ResolveOutcome.Hit, ResolveOutcome.HitAfterFallback -> {
            advance(p.fade)
            requestList.hit()
            if (showClock) {
                val stopped = clock.stop()
                check(stopped < 1f) {
                    "pacing.clockSweep ${p.clockSweep} is too short for the choreography; " +
                            "use clockSweepForInterceptStop() to size it"
                }
            }
            advance(p.fade)
        }
    }

    advance(p.hold)
    if (!isLastOfPass) {

        requestList.clear()
        if (showClock) clock.hide()
        if (outcome == ResolveOutcome.Timeout) window.close()
        advance(p.fade + p.reset * 0.5)
    }
}

private fun Storyboard.resetPass(over: Duration) {
    phase("reset")
    sources.forEach { it.reset(over) }
    config.sources.indices.forEach { linkOf(it).retract(over) }
    chips.forEach { it.reset(over) }
    handoff.retract(over)
    requestList.clear(over)
    window.close()
    clocks.values.forEach { it.hide(over) }
    advance(over + config.pacing.loopGap)
}

private fun Storyboard.finalHold(p: Pacing) {
    advance(p.finalHold)
    resetPass(p.reset)
}

private fun Storyboard.seekAfter(time: Duration) {
    if (time > now) advance(time - now)
}
