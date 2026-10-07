package com.wynime.app.videoplayer.ui.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.dialogs.PlatformPopupProperties
import com.wynime.app.ui.foundation.effects.onPointerEventMultiplatform
import com.wynime.app.ui.foundation.input.asGesturePointerType
import com.wynime.app.ui.foundation.theme.slightlyWeaken
import com.wynime.app.ui.foundation.theme.weaken
import com.wynime.app.videoplayer.ui.gesture.SwipeSeekerConfig
import com.wynime.app.videoplayer.ui.gesture.isVerticalDragCancelled
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.chapters
import org.openani.mediamp.metadata.Chapter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val TAG_PROGRESS_SLIDER_PREVIEW_POPUP = "ProgressSliderPreviewPopup"
const val TAG_PROGRESS_SLIDER_PREVIEW_FRAME = "ProgressSliderPreviewFrame"
const val TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME = "ProgressSliderCenteredPreviewFrame"
const val TAG_PROGRESS_SLIDER = "ProgressSlider"

@Stable
class PlayerProgressSliderState(
    currentPositionMillis: () -> Long,
    totalDurationMillis: () -> Long,
    chapters: () -> List<Chapter>,

    private val onPreview: (positionMillis: Long) -> Unit,

    private val onPreviewFinished: (positionMillis: Long) -> Unit,
) {
    val currentPositionMillis: Long by derivedStateOf(currentPositionMillis)
    val totalDurationMillis: Long by derivedStateOf(totalDurationMillis)
    val chapters by derivedStateOf(chapters)

    private var previewPositionRatio: Float by mutableFloatStateOf(Float.NaN)

    val isPreviewing: Boolean by derivedStateOf {
        !previewPositionRatio.isNaN()
    }

    fun previewPositionRatio(ratio: Float) {
        previewPositionRatio = ratio
        onPreview((totalDurationMillis * ratio).roundToLong())
    }

    val displayPositionRatio by derivedStateOf {
        val previewPositionRatio = this.previewPositionRatio
        if (!previewPositionRatio.isNaN()) {
            return@derivedStateOf previewPositionRatio
        }

        val total = this.totalDurationMillis
        if (total == 0L) {
            return@derivedStateOf 0f
        }
        this.currentPositionMillis.toFloat() / total
    }

    fun finishPreview() {
        val ratio = this.previewPositionRatio
        if (ratio.isNaN()) return
        onPreviewFinished((ratio * totalDurationMillis).roundToLong())
        previewPositionRatio = Float.NaN
    }

    fun cancelPreview() {
        previewPositionRatio = Float.NaN
    }
}

private class Data(
    val currentPosition: Long,
    val mediaProperties: org.openani.mediamp.metadata.MediaProperties?,
    val chapters: List<Chapter>,
) {
    @Stable
    companion object {
        @Stable
        val EMPTY = Data(0, null, emptyList())
    }
}

@Composable
fun rememberMediaProgressSliderState(
    player: MediampPlayer,
    chaptersFlow: Flow<List<Chapter>> = player.chapters ?: flowOf(emptyList()),
    onPreview: (positionMillis: Long) -> Unit,
    onPreviewFinished: (positionMillis: Long) -> Unit,
): PlayerProgressSliderState {

    val flow = remember(player, chaptersFlow) {
        combine(
            player.currentPositionMillis,
            player.mediaProperties,
            chaptersFlow,
            ::Data,
        )
    }

    val data by flow.collectAsStateWithLifecycle(Data.EMPTY)

    val totalDuration by remember {
        derivedStateOf {
            data.mediaProperties?.durationMillis ?: 0L
        }
    }

    val onPreviewUpdated by rememberUpdatedState(onPreview)
    val onPreviewFinishedUpdated by rememberUpdatedState(onPreviewFinished)
    return remember {
        PlayerProgressSliderState(
            { data.currentPosition },
            { totalDuration },
            { data.chapters },
            onPreviewUpdated,
            onPreviewFinishedUpdated,
        )
    }
}

object MediaProgressSliderDefaults {
    @Composable
    fun colors(
        trackBackgroundColor: Color = MaterialTheme.colorScheme.surface,
        trackProgressColor: Color = MaterialTheme.colorScheme.primary,
        thumbColor: Color = MaterialTheme.colorScheme.primary,
        cachedProgressColor: Color = MaterialTheme.colorScheme.onSurface.weaken(),
        downloadingColor: Color = Color.Yellow,
        notAvailableColor: Color = MaterialTheme.colorScheme.error.slightlyWeaken(),
        chapterColor: Color = MaterialTheme.colorScheme.onSurface,
        previewTimeBackgroundColor: Color = MaterialTheme.colorScheme.surface,
        previewTimeTextColor: Color = MaterialTheme.colorScheme.onSurface,
    ): MediaProgressSliderColors {
        return MediaProgressSliderColors(
            trackBackgroundColor,
            trackProgressColor,
            thumbColor,
            cachedProgressColor,
            downloadingColor,
            notAvailableColor,
            chapterColor,
            previewTimeBackgroundColor,
            previewTimeTextColor,
        )
    }
}

@Immutable
class MediaProgressSliderColors(
    val trackBackgroundColor: Color,
    val trackProgressColor: Color,
    val thumbColor: Color,
    val cachedProgressColor: Color,
    val downloadingColor: Color,
    val notAvailableColor: Color,
    val chapterColor: Color,
    val previewTimeBackgroundColor: Color,
    val previewTimeTextColor: Color,
)

@Stable
class TouchSeekState(
    swipeSeekerConfig: SwipeSeekerConfig,
    density: Density,
    val onStateChanged: (State) -> Unit,
) {
    private val cancelVerticalDragDistancePx =
        with(density) { swipeSeekerConfig.cancelVerticalDragDistance.toPx() }

    enum class State {
        Idle,
        Seeking,
        Cancelling,
    }

    var state: State = State.Idle
        private set

    private var dragStartY: Float = Float.NaN

    internal fun onPointerDown(position: Offset) {
        if (state == State.Idle && position.isSpecified) {
            dragStartY = position.y
        }
    }

    internal fun start() {
        transitionTo(State.Seeking)
    }

    internal fun move(position: Offset): Boolean {
        val cancelling = isVerticalDragCancelled(
            dragStartY,
            position,
            cancelVerticalDragDistancePx,
        )
        return transitionTo(if (cancelling) State.Cancelling else State.Seeking)
    }

    internal fun stop(): Boolean {
        val cancelled = state == State.Cancelling
        transitionTo(State.Idle)
        dragStartY = Float.NaN
        return cancelled
    }

    private fun transitionTo(newState: State): Boolean {
        if (state == newState) return false
        state = newState
        onStateChanged(newState)
        return true
    }
}

@Composable
fun MediaProgressSlider(
    state: PlayerProgressSliderState,
    cacheProgressInfoFlow: () -> MediaCacheProgressInfo?,
    colors: MediaProgressSliderColors = MediaProgressSliderDefaults.colors(),
    enabled: Boolean = true,
    showPreviewTimeTextOnThumb: Boolean = true,
    framePreview: MediaProgressFramePreviewState? = null,
    showFramePreviewInPopup: Boolean = true,
    touchSeekState: TouchSeekState? = null,

    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth()
            .height(24.dp)
            .testTag(TAG_PROGRESS_SLIDER),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .padding(horizontal = 2.dp)
                .clip(CircleShape),
        ) {
            Canvas(Modifier.matchParentSize()) {

                drawRect(
                    colors.trackBackgroundColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                )
            }

            Canvas(Modifier.matchParentSize()) {

                val snapshotCacheProgress = cacheProgressInfoFlow() ?: return@Canvas

                var currentX = 0f

                forEachConsecutiveChunk(snapshotCacheProgress) { state, weight ->
                    val color = when (state) {
                        ChunkState.NONE -> Color.Unspecified
                        ChunkState.DOWNLOADING -> colors.downloadingColor
                        ChunkState.DONE -> colors.cachedProgressColor
                        ChunkState.NOT_AVAILABLE -> colors.notAvailableColor
                    }
                    if (color != Color.Unspecified) {
                        val size = Size(
                            weight * size.width,
                            size.height,
                        )
                        drawRect(
                            color,
                            topLeft = Offset(currentX, 0f),
                            size = size,
                        )
                    }
                    currentX += weight * size.width
                }
            }

            Canvas(Modifier.matchParentSize()) {

                val xPlay = size.width * state.displayPositionRatio

                drawRect(
                    colors.trackProgressColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(xPlay, size.height),
                )

            }

            Canvas(Modifier.matchParentSize()) {
                if (state.totalDurationMillis == 0L) return@Canvas
                state.chapters.forEach { chapter ->
                    fun drawChapterMarker(millis: Long) {
                        val percent = millis.toFloat().div(state.totalDurationMillis)
                        drawCircle(
                            color = colors.chapterColor,
                            radius = 2.dp.toPx(),
                            center = Offset(size.width * percent, this.center.y),
                            blendMode = BlendMode.Src,
                        )
                    }
                    drawChapterMarker(chapter.offsetMillis)

                    val endMillis = chapter.offsetMillis + chapter.durationMillis
                    if (state.chapters.none { it.offsetMillis == endMillis }) {
                        drawChapterMarker(endMillis)
                    }
                }
            }
        }

        var mousePosX by rememberSaveable { mutableStateOf(0f) }
        var thumbWidth by rememberSaveable { mutableIntStateOf(0) }
        var sliderWidth by rememberSaveable { mutableIntStateOf(0) }
        var latestTouchPreviewRatio by remember { mutableFloatStateOf(Float.NaN) }
        var handlingTouchInput by remember { mutableStateOf(false) }

        fun renderPreviewTime(previewTimeMillis: Long): String {
            state.chapters.find {
                previewTimeMillis in it.offsetMillis..<it.offsetMillis + it.durationMillis
            }?.let {
                val chapterName = if (it.name.isBlank()) "" else it.name + "\n"
                return chapterName + renderSeconds(
                    previewTimeMillis / 1000,
                    state.totalDurationMillis / 1000,
                ).substringBefore(" ")
            }

            return renderSeconds(previewTimeMillis / 1000, state.totalDurationMillis / 1000).substringBefore(" ")
        }

        val previewTimeText by remember {
            derivedStateOf {
                val containerWidth = sliderWidth - thumbWidth
                if (containerWidth == 0) {
                    ""
                } else {
                    val percent = mousePosX.minus(thumbWidth / 2).div(containerWidth)
                        .coerceIn(0f, 1f)
                    val previewTimeMillis = state.totalDurationMillis.times(percent).toLong()

                    renderPreviewTime(previewTimeMillis)
                }
            }
        }
        val previewTimeOnThumb by remember(state) {
            derivedStateOf {
                val previewTimeMillis = state.totalDurationMillis.times(state.displayPositionRatio).toLong()

                renderPreviewTime(previewTimeMillis)
            }
        }
        val hoverInteraction = remember { MutableInteractionSource() }
        val isHovered by hoverInteraction.collectIsHoveredAsState()
        var isPressed by remember { mutableStateOf(false) }
        val showPreviewTime by remember {
            derivedStateOf {
                isHovered || isPressed
            }
        }
        if (framePreview != null) {

            val previewingPositionMillis by remember(state) {
                derivedStateOf {
                    when {
                        state.isPreviewing && showPreviewTimeTextOnThumb ->
                            (state.totalDurationMillis * state.displayPositionRatio).toLong()

                        showPreviewTime -> {
                            val containerWidth = sliderWidth - thumbWidth
                            if (containerWidth <= 0) {
                                null
                            } else {
                                val percent = mousePosX.minus(thumbWidth / 2).div(containerWidth)
                                    .coerceIn(0f, 1f)
                                (state.totalDurationMillis * percent).toLong()
                            }
                        }

                        else -> null
                    }
                }
            }
            LaunchedEffect(framePreview, state) {
                snapshotFlow { previewingPositionMillis }
                    .collectLatest { positionMillis ->
                        if (positionMillis == null) {
                            framePreview.onPreviewFinished()
                            return@collectLatest
                        }
                        val total = state.totalDurationMillis
                        if (total <= 0) return@collectLatest

                        if (!cacheProgressInfoFlow().isPositionCached(positionMillis.toFloat() / total)) {
                            return@collectLatest
                        }
                        framePreview.requestFrame(positionMillis)
                    }
            }
        }
        if (showPreviewTime) {
            val showFrame = showFramePreviewInPopup && framePreview != null
            ProgressSliderPreviewPopup(
                offsetX = { mousePosX.roundToInt() },
                previewTimeBackgroundColor = colors.previewTimeBackgroundColor,
                shape = previewPopupShape(showFrame),
            ) {
                ProgressSliderPreviewContent(
                    frame = framePreview?.frame,
                    text = previewTimeText,
                    previewTimeTextColor = colors.previewTimeTextColor,
                    showFrame = showFrame,
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = state.displayPositionRatio,
            valueRange = 0f..1f,
            onValueChange = {
                if (handlingTouchInput && touchSeekState?.state == TouchSeekState.State.Idle) {
                    touchSeekState.start()
                }
                latestTouchPreviewRatio = it
                if (touchSeekState?.state != TouchSeekState.State.Cancelling) {
                    state.previewPositionRatio(it)
                }
            },
            interactionSource = interactionSource,
            thumb = {
                Canvas(Modifier.width(12.dp).height(24.dp)) {
                    drawCircle(
                        colors.thumbColor,
                        radius = 8.dp.toPx(),
                    )
                }

                if (state.isPreviewing && showPreviewTimeTextOnThumb) {
                    val showFrame = showFramePreviewInPopup && framePreview != null
                    ProgressSliderPreviewPopup(
                        offsetX = { thumbWidth / 2 },
                        previewTimeBackgroundColor = colors.previewTimeBackgroundColor,
                        shape = previewPopupShape(showFrame),
                    ) {
                        ProgressSliderPreviewContent(
                            frame = framePreview?.frame,
                            text = previewTimeOnThumb,
                            previewTimeTextColor = colors.previewTimeTextColor,
                            showFrame = showFrame,
                        )
                    }
                }
            },
            track = {
                SliderDefaults.Track(
                    it,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                    ),
                )
            },
            onValueChangeFinished = {
                val cancelled = handlingTouchInput && touchSeekState?.stop() == true
                handlingTouchInput = false
                if (cancelled) {
                    state.cancelPreview()
                } else {
                    state.finishPreview()
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(24.dp)
                .onSizeChanged {
                    sliderWidth = it.width
                }
                .hoverable(interactionSource = hoverInteraction)
                .onPointerEventMultiplatform(
                    PointerEventType.Press,
                    pass = PointerEventPass.Initial,
                ) { event ->
                    val touchChange = event.changes.firstOrNull()
                        ?.takeIf { it.type.asGesturePointerType() == PointerType.Touch }
                    handlingTouchInput = touchChange != null
                    touchChange?.let { touchSeekState?.onPointerDown(it.position) }
                }
                .onPointerEventMultiplatform(
                    PointerEventType.Move,
                    pass = PointerEventPass.Initial,
                ) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEventMultiplatform
                    mousePosX = change.position.x

                    val touchSeekState = touchSeekState ?: return@onPointerEventMultiplatform
                    if (!handlingTouchInput || touchSeekState.state == TouchSeekState.State.Idle) {
                        return@onPointerEventMultiplatform
                    }
                    if (!touchSeekState.move(change.position)) {
                        return@onPointerEventMultiplatform
                    }

                    if (touchSeekState.state == TouchSeekState.State.Cancelling) {
                        state.cancelPreview()
                    } else if (!latestTouchPreviewRatio.isNaN()) {
                        state.previewPositionRatio(latestTouchPreviewRatio)
                    }
                },
        )
    }
}

@Composable
private fun ProgressSliderPreviewContent(
    frame: ImageBitmap?,
    text: String,
    previewTimeTextColor: Color,
    showFrame: Boolean,
) {
    if (showFrame) {
        PreviewFrameAndTimeText(
            frame = frame,
            text = text,
            previewTimeTextColor = previewTimeTextColor,
            showFrameArea = true,
        )
    } else {
        PreviewTimeText(text, previewTimeTextColor)
    }
}

@Composable
internal fun previewPopupShape(hasFrame: Boolean): Shape =
    if (hasFrame) RoundedCornerShape(12.dp) else CircleShape

@Composable
fun ProgressSliderPreviewPopup(
    offsetX: () -> Int,
    previewTimeBackgroundColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val popupPositionProviderState = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val anchor = IntRect(
                    offset = IntOffset(
                        offsetX(),
                        with(density) { -8.dp.toPx().toInt() },
                    ) + anchorBounds.topLeft,
                    size = IntSize.Zero,
                )
                val tooltipArea = IntRect(
                    IntOffset(
                        anchor.left - popupContentSize.width,
                        anchor.top - popupContentSize.height,
                    ),
                    IntSize(
                        popupContentSize.width * 2,
                        popupContentSize.height * 2,
                    ),
                )
                val position = Alignment.TopCenter.align(popupContentSize, tooltipArea.size, layoutDirection)

                return IntOffset(
                    x = (tooltipArea.left + position.x).coerceIn(0, windowSize.width - popupContentSize.width),
                    y = (tooltipArea.top + position.y).coerceIn(0, windowSize.height - popupContentSize.height),
                )
            }
        }
    }
    Popup(
        properties = PlatformPopupProperties(usePlatformInsets = false),
        popupPositionProvider = popupPositionProviderState,
    ) {
        Box(
            modifier = modifier
                .testTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP)
                .clip(shape = shape)
                .background(previewTimeBackgroundColor)
                .animateContentSize(),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun PreviewProgressSliderPreviewPopup() = ProvideCompositionLocalsForPreview {
    val colors = MediaProgressSliderDefaults.colors()
    val offsetX = with(LocalDensity.current) { 120.dp.roundToPx() }
    Box(
        Modifier
            .size(width = 240.dp, height = 80.dp)
            .background(Color.Black),
    ) {
        ProgressSliderPreviewPopup(
            offsetX = { offsetX },
            previewTimeBackgroundColor = colors.previewTimeBackgroundColor,
        ) {
            PreviewTimeText("12:34", colors.previewTimeTextColor)
        }
    }
}

@Composable
fun PreviewFrameAndTimeText(
    frame: ImageBitmap?,
    text: String,
    previewTimeTextColor: Color,
    showFrameArea: Boolean = frame != null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showFrameArea) {
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                if (frame != null) {
                    Image(
                        frame,
                        contentDescription = null,
                        Modifier
                            .matchParentSize()
                            .testTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        PreviewTimeText(text, previewTimeTextColor)
    }
}

@Composable
fun ProgressSliderCenteredPreviewFrame(
    frame: ImageBitmap?,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    if (frame == null) return

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(width = 160.dp, height = 90.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.3f))
            .border(2.dp, borderColor, shape)
            .testTag(TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            frame,
            contentDescription = null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Preview
@Composable
private fun PreviewFrameAndTimeTextContent() = ProvideCompositionLocalsForPreview {
    val colors = MediaProgressSliderDefaults.colors()
    Box(
        Modifier
            .background(colors.previewTimeBackgroundColor)
            .padding(16.dp),
    ) {
        PreviewFrameAndTimeText(
            frame = null,
            text = "12:34",
            previewTimeTextColor = colors.previewTimeTextColor,
            showFrameArea = true,
        )
    }
}

internal fun MediaCacheProgressInfo?.isPositionCached(ratio: Float): Boolean {
    if (this == null || isEmpty()) return true
    var accumulated = 0f
    for (i in 0..lastIndex) {
        accumulated += chunkWeights[i]
        if (ratio <= accumulated) return chunkStates[i] == ChunkState.DONE
    }
    return chunkStates[lastIndex] == ChunkState.DONE
}

@Composable
fun PreviewTimeText(
    text: String,
    previewTimeTextColor: Color,
) {
    Box(contentAlignment = Alignment.Center) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Text(

                text = text,
                Modifier.alpha(0f),
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = text,
                color = previewTimeTextColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private inline fun forEachConsecutiveChunk(
    chunks: MediaCacheProgressInfo,
    action: (state: ChunkState, weight: Float) -> Unit
) {
    if (chunks.isEmpty()) return

    var currentState: ChunkState = chunks.chunkStates[0]
    var start = 0
    var end = 0

    for (index in 1..chunks.lastIndex) {
        val chunk = chunks.chunkStates[index]
        if (chunk != currentState) {
            action(currentState, chunks.sumWeightOfRange(start, end + 1))
            currentState = chunk
            start = index
        }
        end = index
    }

    action(currentState, chunks.sumWeightOfRange(start, end + 1))
}

private fun MediaCacheProgressInfo.sumWeightOfRange(start: Int, endExclusive: Int): Float {
    var sum: Float = 0.toFloat()
    for (i in start until endExclusive) {
        sum += chunkWeights[i]
    }
    return sum
}

@OverloadResolutionByLambdaReturnType
inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum: Float = 0.toFloat()
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
