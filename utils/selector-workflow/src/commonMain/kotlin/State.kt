package com.wynime.utils.selectorworkflow

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
data class SelectorWorkflowState(

    val time: Duration,

    val duration: Duration,

    val phase: String,
    val sourceNodes: List<SourceNodeState>,
    val sourceLinks: List<LineState>,
    val results: List<ResultChipState>,
    val ripples: List<RippleState>,
    val cursors: List<CursorState>,
    val handoff: LineState,
    val window: WindowState,
    val requestRows: List<RequestRowState>,
    val scroll: ScrollState,
    val clocks: Map<ClockId, ClockState>,

    val highlights: Set<HighlightRegion> = emptySet(),
) {
    val progress: Float
        get() = if (duration <= Duration.ZERO) 0f
        else (time.inWholeMicroseconds.toFloat() / duration.inWholeMicroseconds).coerceIn(0f, 1f)
}

@Immutable
data class SourceNodeState(
    val index: Int,
    val alpha: Float,

    val pulsing: Boolean,

    val priority: Boolean,
)

enum class LineTone {

    Source,

    Cached,
}

@Immutable
data class LineState(
    val progress: Float,
    val alpha: Float,
    val tone: LineTone = LineTone.Source,
    val previousTone: LineTone = tone,
    val toneBlend: Float = 1f,
) {
    companion object {
        val Hidden = LineState(progress = 0f, alpha = 0f)
    }
}

enum class ChipTone {

    Source,

    Selected,

    Failed,
}

@Immutable
data class ResultChipState(
    val key: ResultKey,
    val cell: Int,
    val alpha: Float,
    val tone: ChipTone,
    val scale: Float,
    val candidate: Boolean,
    val priority: Boolean,
    val previousTone: ChipTone = tone,
    val toneBlend: Float = 1f,
)

enum class RippleTarget {

    Result,

    RequestRow,

    SourceNode,
}

@Immutable
data class RippleState(
    val target: RippleTarget,
    val index: Int,
    val scale: Float,
    val alpha: Float,
)

@Immutable
data class CursorState(
    val id: String,
    val owner: Int?,
    val cell: Float,
    val alpha: Float,
)

enum class ClockTone {

    Running,

    Stopped,

    Expired,
}

@Immutable
data class ClockState(
    val id: ClockId,
    val alpha: Float,
    val sweep: Float,
    val tone: ClockTone,
    val overlayAlpha: Float,
    val budgetSeconds: Float,
    val previousTone: ClockTone = tone,
    val toneBlend: Float = 1f,
) {

    val handDegrees: Float get() = sweep * 360f

    val elapsedSeconds: Float get() = sweep * budgetSeconds

    val hasReadout: Boolean get() = budgetSeconds > 0f

    companion object {
        fun hidden(id: ClockId) = ClockState(
            id, alpha = 0f, sweep = 0f, tone = ClockTone.Running, overlayAlpha = 0f, budgetSeconds = 0f,
        )
    }
}

enum class WindowTone {

    Closed,

    Open,

    Failed,
}

@Immutable
data class WindowState(
    val tone: WindowTone,
    val previousTone: WindowTone = tone,
    val toneBlend: Float = 1f,
)

enum class RequestIcon {

    Request,

    Media,
}

enum class RequestTone { Idle, Hit }

@Immutable
data class RequestRowState(
    val index: Int,
    val alpha: Float,
    val icon: RequestIcon,
    val tone: RequestTone,

    val iconScale: Float,
    val previousTone: RequestTone = tone,
    val toneBlend: Float = 1f,
)

@Immutable
data class ScrollState(val rowOffset: Float)
