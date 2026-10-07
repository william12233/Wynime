package com.wynime.utils.selectorworkflow

import com.wynime.utils.selectorworkflow.anim.Easings
import com.wynime.utils.selectorworkflow.anim.ToneChannel
import com.wynime.utils.selectorworkflow.anim.floatTrack
import com.wynime.utils.selectorworkflow.anim.stepTrack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class Storyboard internal constructor(
    val config: SelectorWorkflowConfig,
) {
    private val pacing get() = config.pacing

    var now: Duration = Duration.ZERO
        private set

    private var high: Duration = Duration.ZERO

    private val phaseTrack = stepTrack("idle")

    val sources: List<SourceHandle> = config.sources.indices.map { SourceHandle(it) }
    private val sourceLinks: List<LineHandle> = config.sources.indices.map { LineHandle() }
    val chips: List<ChipHandle> = config.results.mapIndexed { cell, key -> ChipHandle(key, cell) }
    val ripples: List<RippleHandle> = config.candidates.map {
        RippleHandle(RippleTarget.Result, config.cellOf(it))
    }

    val requestRipple = RippleHandle(RippleTarget.RequestRow, config.resolve.hitRow)

    val sourceRipples: List<RippleHandle> = config.sources.indices.map {
        RippleHandle(RippleTarget.SourceNode, it)
    }
    val handoff = LineHandle()
    val window = WindowHandle()
    val requestList = RequestListHandle()
    val clocks: Map<ClockId, ClockHandle> = ClockId.entries.associateWith { ClockHandle(it) }

    private val cursorHandles = LinkedHashMap<String, CursorHandle>()

    fun cursor(id: String, owner: Int? = null): CursorHandle =
        cursorHandles.getOrPut(id) { CursorHandle(id, owner) }

    fun linkOf(index: Int): LineHandle = sourceLinks[index]

    fun chipsOf(source: Int): List<ChipHandle> = chips.filter { it.key.source == source }

    fun chipOf(key: ResultKey): ChipHandle = chips.first { it.key == key }

    fun rippleAt(cell: Int): RippleHandle = ripples.first { it.index == cell }

    fun sourceRipple(index: Int): RippleHandle = sourceRipples[index]

    fun advance(by: Duration) {
        require(by >= Duration.ZERO) { "cannot advance backwards: $by" }
        now += by
        touch(now)
    }

    fun at(time: Duration, block: Storyboard.() -> Unit) {
        val saved = now
        now = time
        touch(time)
        block()
        now = saved
    }

    fun phase(label: String) {
        phaseTrack.key(now, label)
    }

    private fun touch(time: Duration) {
        if (time > high) high = time
    }

    inner class SourceHandle internal constructor(val index: Int) {
        internal val alpha = floatTrack(1f)
        internal val pulsing = stepTrack(false)

        val spec: SourceSpec get() = config.sources[index]

        fun beginSearch() {
            alpha.key(now, 1f)
            pulsing.key(now, true)
            touch(now)
        }

        fun settle() {
            pulsing.key(now, false)
            alpha.ramp(now, pacing.fade, DIM_ALPHA)
            touch(now + pacing.fade)
        }

        fun reset(over: Duration = pacing.reset) {
            pulsing.key(now, false)
            alpha.ramp(now, over, 1f)
            touch(now + over)
        }
    }

    inner class LineHandle internal constructor() {
        internal val progress = floatTrack(0f)
        internal val alpha = floatTrack(0f)
        internal val tone = ToneChannel(LineTone.Source)

        fun markCached(over: Duration = pacing.fade * 0.5) {
            tone.shift(LineTone.Cached, now, over)
            touch(now + over)
        }

        fun draw(over: Duration) {
            progress.key(now, progress.valueAt(now))
            progress.key(now + SNAP, 0f, Easings.Linear)
            alpha.key(now, 0f)
            alpha.ramp(now, SNAP, 1f)
            progress.key(now + SNAP + over, 1f)
            touch(now + SNAP + over)
        }

        fun mute(over: Duration = pacing.fade) {
            alpha.ramp(now, over, MUTED_ALPHA)
            touch(now + over)
        }

        fun retract(over: Duration = pacing.reset) {
            alpha.ramp(now, over, 0f)
            progress.ramp(now, over, 0f)

            tone.snap(LineTone.Source, now + over)
            touch(now + over)
        }
    }

    inner class ChipHandle internal constructor(val key: ResultKey, val cell: Int) {
        internal val alpha = floatTrack(0f)
        internal val tone = ToneChannel(ChipTone.Source)
        internal val scale = floatTrack(1f)

        val candidate: Boolean get() = key.isCandidate(config)
        val priority: Boolean get() = config.showPriorityMarks && config.sources[key.source].priority

        fun appear(over: Duration = pacing.fade, target: Float = 1f) {
            alpha.ramp(now, over, target)
            touch(now + over)
        }

        fun select() {
            tone.shift(ChipTone.Selected, now, pacing.fade * 0.7)
            alpha.ramp(now, pacing.fade, 1f)
            scale.key(now, 1f, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop * 0.4, SELECT_POP, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop, 1f)
            touch(now + pacing.pop)
        }

        fun fail(over: Duration = pacing.fade * 0.8) {
            tone.shift(ChipTone.Failed, now, over)
            touch(now + over)
        }

        fun mute(over: Duration = pacing.fade) {
            alpha.ramp(now, over, MUTED_ALPHA)
            touch(now + over)
        }

        fun reset(over: Duration = pacing.reset) {
            alpha.ramp(now, over, 0f)
            tone.snap(ChipTone.Source, now + over)
            scale.ramp(now, over, 1f)
            touch(now + over)
        }
    }

    inner class RippleHandle internal constructor(val target: RippleTarget, val index: Int) {
        internal val scale = floatTrack(RIPPLE_FROM)
        internal val alpha = floatTrack(0f)

        fun pulse(over: Duration = pacing.ripple) {
            scale.key(now, RIPPLE_FROM)
            alpha.key(now, 0f)
            alpha.key(now + SNAP, RIPPLE_ALPHA)
            scale.key(now + over, RIPPLE_TO)
            alpha.key(now + over, 0f)
            touch(now + over)
        }
    }

    inner class CursorHandle internal constructor(val id: String, val owner: Int?) {
        internal val cell = floatTrack(0f)
        internal val alpha = floatTrack(0f)

        fun enter(atCell: Int, peakAlpha: Float = 1f) {
            cell.key(now, atCell.toFloat())
            alpha.key(now, 0f)
            alpha.key(now + SNAP, peakAlpha)
            touch(now + SNAP)
        }

        fun step(toCell: Int, over: Duration = pacing.cursorStep) {
            cell.key(now, cell.valueAt(now), Easings.CursorHop)
            cell.key(now + over, toCell.toFloat())
            touch(now + over)
        }

        fun leave(over: Duration = pacing.cursorExit) {
            alpha.ramp(now, over, 0f)
            touch(now + over)
        }
    }

    inner class ClockHandle internal constructor(val id: ClockId) {
        internal val alpha = floatTrack(0f)
        internal val sweep = floatTrack(0f)
        internal val tone = ToneChannel(ClockTone.Running)
        internal val overlay = floatTrack(0f)

        private var startedAt: Duration = Duration.ZERO
        private var window: Duration = Duration.ZERO

        fun start() {
            startedAt = now
            window = pacing.clockSweep
            tone.snap(ClockTone.Running, now)
            overlay.key(now, 0f)
            alpha.key(now, 0f)
            alpha.ramp(now, SNAP, 1f)
            sweep.key(now, 0f, Easings.Linear)
            sweep.key(now + window, 1f)
            touch(now + window)
        }

        val fullSweepDuration: Duration get() = window

        val elapsed: Duration get() = now - startedAt

        fun stop(): Float {
            check(window > Duration.ZERO) { "clock ${id.name} was not started" }
            val used = (now - startedAt).coerceAtLeast(Duration.ZERO)
            val fraction = (used.inWholeMicroseconds.toFloat() / window.inWholeMicroseconds).coerceIn(0f, 1f)
            sweep.key(now, fraction)
            tone.shift(ClockTone.Stopped, now, pacing.fade)
            touch(now + pacing.fade)
            return fraction
        }

        fun expire() {
            sweep.key(now, 1f)
            tone.shift(ClockTone.Expired, now, pacing.fade)
            overlay.ramp(now, pacing.fade, TIMEOUT_OVERLAY)
            touch(now + pacing.fade)
        }

        fun hide(over: Duration = pacing.fade) {
            alpha.ramp(now, over, 0f)
            tone.snap(ClockTone.Running, now + over)
            overlay.ramp(now, over, 0f)
            sweep.ramp(now, over, 0f)
            window = Duration.ZERO
            touch(now + over)
        }
    }

    inner class WindowHandle internal constructor() {
        internal val tone = ToneChannel(WindowTone.Closed)

        fun open(after: Duration = pacing.windowOpen) {
            tone.shift(WindowTone.Open, now + after, pacing.fade)
            touch(now + after + pacing.fade)
        }

        fun markFailed(after: Duration = pacing.fade * 0.7) {
            tone.shift(WindowTone.Failed, now + after, pacing.fade)
            touch(now + after + pacing.fade)
        }

        fun close(over: Duration = pacing.fade) {
            tone.shift(WindowTone.Closed, now, over)
            touch(now + over)
        }
    }

    inner class RequestListHandle internal constructor() {
        internal val rowAlpha = List(config.resolve.requestCount) { floatTrack(0f) }
        internal val rowIcon = List(config.resolve.requestCount) { index ->
            stepTrack(if (index == config.resolve.hitRow) RequestIcon.Media else RequestIcon.Request)
        }
        internal val rowTone = List(config.resolve.requestCount) { ToneChannel(RequestTone.Idle) }
        internal val rowIconScale = List(config.resolve.requestCount) { floatTrack(1f) }
        internal val scroll = floatTrack(0f)

        fun stream(
            stagger: Duration = pacing.rowStagger,
            fade: Duration = pacing.fade,
            mediaRowIcon: RequestIcon = RequestIcon.Media,
        ): Duration {
            rowIcon[config.resolve.hitRow].key(now, mediaRowIcon)
            var last = now
            rowAlpha.forEachIndexed { index, track ->
                val at = now + stagger * index.toDouble()
                track.ramp(at, fade, 1f)
                last = at + fade
            }
            touch(last)
            return last
        }

        fun scrollTo(row: Int, over: Duration = pacing.scroll) {
            val maxOffset = (config.resolve.requestCount - config.resolve.visibleRows).coerceAtLeast(0)
            val target = (row - config.resolve.visibleRows / 2).coerceIn(0, maxOffset)
            scroll.key(now, scroll.valueAt(now), Easings.Standard)
            scroll.key(now + over, target.toFloat())
            touch(now + over)
        }

        fun hit(over: Duration = pacing.fade * 0.7) {
            val row = config.resolve.hitRow
            rowTone[row].shift(RequestTone.Hit, now, over)
            val scale = rowIconScale[row]
            scale.key(now, 1f, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop * 0.4, HIT_POP, Easings.EmphasizedDecelerate)
            scale.key(now + pacing.pop, 1f)
            requestRipple.pulse()
            touch(now + pacing.pop)
        }

        fun clear(over: Duration = pacing.fade) {
            rowAlpha.forEach { it.ramp(now, over, 0f) }
            rowTone.forEach { it.snap(RequestTone.Idle, now + over) }
            rowIconScale.forEach { it.key(now + over, 1f) }
            scroll.ramp(now, over, 0f)
            touch(now + over)
        }
    }

    internal fun build(): SelectorWorkflowTimeline = SelectorWorkflowTimeline(
        config = config,
        duration = high + pacing.loopGap,
        phase = phaseTrack.build(),
        nodes = sources.map {
            NodeTracks(
                it.index, it.alpha.build(), it.pulsing.build(),
                priority = config.showPriorityMarks && config.sources[it.index].priority,
            )
        },
        links = sourceLinks.map { LineTracks(it.progress.build(), it.alpha.build(), it.tone.build()) },
        chips = chips.map {
            ChipTracks(
                it.key, it.cell, it.candidate, it.priority,
                it.alpha.build(), it.tone.build(), it.scale.build(),
            )
        },
        ripples = (ripples + requestRipple + sourceRipples).map {
            RippleTracks(it.target, it.index, it.scale.build(), it.alpha.build())
        },
        cursors = cursorHandles.values.map {
            CursorTracks(it.id, it.owner, it.cell.build(), it.alpha.build())
        },
        handoff = LineTracks(handoff.progress.build(), handoff.alpha.build(), handoff.tone.build()),
        window = window.tone.build(),
        rows = List(config.resolve.requestCount) { i ->
            RowTracks(
                i,
                requestList.rowAlpha[i].build(),
                requestList.rowIcon[i].build(),
                requestList.rowTone[i].build(),
                requestList.rowIconScale[i].build(),
            )
        },
        scroll = requestList.scroll.build(),
        clocks = clocks.mapValues { (id, h) ->
            ClockTracks(
                id, h.alpha.build(), h.sweep.build(), h.tone.build(), h.overlay.build(),
                budgetSeconds = when (id) {
                    ClockId.PriorityWait -> config.selection.priorityWait
                    ClockId.InterceptBudget -> config.resolve.budget
                }?.let { (it.inWholeMilliseconds / 1000.0).toFloat() } ?: 0f,
            )
        },
    )

    companion object {

        val SNAP: Duration = 20.milliseconds
        const val DIM_ALPHA = 0.35f
        const val MUTED_ALPHA = 0.4f
        const val SELECT_POP = 1.16f
        const val HIT_POP = 1.3f
        const val RIPPLE_FROM = 0.9f
        const val RIPPLE_TO = 1.9f
        const val RIPPLE_ALPHA = 0.9f
        const val TIMEOUT_OVERLAY = 0.22f
    }
}
