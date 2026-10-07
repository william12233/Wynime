package com.wynime.utils.selectorworkflow

import androidx.compose.runtime.Immutable
import com.wynime.utils.selectorworkflow.anim.ToneTracks
import com.wynime.utils.selectorworkflow.anim.Track
import kotlin.time.Duration

@Immutable
class SelectorWorkflowTimeline internal constructor(
    val config: SelectorWorkflowConfig,
    val duration: Duration,
    internal val phase: Track<String>,
    internal val nodes: List<NodeTracks>,
    internal val links: List<LineTracks>,
    internal val chips: List<ChipTracks>,
    internal val ripples: List<RippleTracks>,
    internal val cursors: List<CursorTracks>,
    internal val handoff: LineTracks,
    internal val window: ToneTracks<WindowTone>,
    internal val rows: List<RowTracks>,
    internal val scroll: Track<Float>,
    internal val clocks: Map<ClockId, ClockTracks>,
) {
    fun sampleAt(time: Duration): SelectorWorkflowState {
        val t = time.coerceIn(Duration.ZERO, duration)
        return SelectorWorkflowState(
            time = t,
            duration = duration,
            phase = phase.valueAt(t),
            sourceNodes = nodes.map { it.sample(t) },
            sourceLinks = links.map { it.sample(t) },
            results = chips.map { it.sample(t) },
            ripples = ripples.map { it.sample(t) },
            cursors = cursors.map { it.sample(t) },
            handoff = handoff.sample(t),
            window = WindowState(window.current(t), window.previous(t), window.blend(t)),
            requestRows = rows.map { it.sample(t) },
            scroll = ScrollState(scroll.valueAt(t)),
            clocks = clocks.mapValues { (_, tracks) -> tracks.sample(t) },
            highlights = config.highlights,
        )
    }
}

internal class NodeTracks(
    private val index: Int,
    private val alpha: Track<Float>,
    private val pulsing: Track<Boolean>,
    private val priority: Boolean,
) {
    fun sample(t: Duration) = SourceNodeState(index, alpha.valueAt(t), pulsing.valueAt(t), priority)
}

internal class LineTracks(
    private val progress: Track<Float>,
    private val alpha: Track<Float>,
    private val tone: ToneTracks<LineTone>,
) {
    fun sample(t: Duration) = LineState(
        progress.valueAt(t), alpha.valueAt(t),
        tone = tone.current(t), previousTone = tone.previous(t), toneBlend = tone.blend(t),
    )
}

internal class ChipTracks(
    private val key: ResultKey,
    private val cell: Int,
    private val candidate: Boolean,
    private val priority: Boolean,
    private val alpha: Track<Float>,
    private val tone: ToneTracks<ChipTone>,
    private val scale: Track<Float>,
) {
    fun sample(t: Duration) = ResultChipState(
        key = key,
        cell = cell,
        alpha = alpha.valueAt(t),
        tone = tone.current(t),
        scale = scale.valueAt(t),
        candidate = candidate,
        priority = priority,
        previousTone = tone.previous(t),
        toneBlend = tone.blend(t),
    )
}

internal class RippleTracks(
    private val target: RippleTarget,
    private val index: Int,
    private val scale: Track<Float>,
    private val alpha: Track<Float>,
) {
    fun sample(t: Duration) = RippleState(target, index, scale.valueAt(t), alpha.valueAt(t))
}

internal class CursorTracks(
    private val id: String,
    private val owner: Int?,
    private val cell: Track<Float>,
    private val alpha: Track<Float>,
) {
    fun sample(t: Duration) = CursorState(id, owner, cell.valueAt(t), alpha.valueAt(t))
}

internal class RowTracks(
    private val index: Int,
    private val alpha: Track<Float>,
    private val icon: Track<RequestIcon>,
    private val tone: ToneTracks<RequestTone>,
    private val iconScale: Track<Float>,
) {
    fun sample(t: Duration) = RequestRowState(
        index, alpha.valueAt(t), icon.valueAt(t), tone.current(t), iconScale.valueAt(t),
        previousTone = tone.previous(t), toneBlend = tone.blend(t),
    )
}

internal class ClockTracks(
    private val id: ClockId,
    private val alpha: Track<Float>,
    private val sweep: Track<Float>,
    private val tone: ToneTracks<ClockTone>,
    private val overlay: Track<Float>,

    private val budgetSeconds: Float,
) {
    fun sample(t: Duration) = ClockState(
        id = id,
        alpha = alpha.valueAt(t),
        sweep = sweep.valueAt(t),
        tone = tone.current(t),
        overlayAlpha = overlay.valueAt(t),
        budgetSeconds = budgetSeconds,
        previousTone = tone.previous(t),
        toneBlend = tone.blend(t),
    )
}
