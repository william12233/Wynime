package com.wynime.app.videoplayer.ui.gesture

import androidx.annotation.UiThread
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.systemGesturesPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.wynime.app.tools.rememberUiMonoTasker
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.effects.onPointerEventMultiplatform
import com.wynime.app.ui.foundation.ifNotNullThen
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.LocalActiveInputSource
import com.wynime.app.ui.foundation.input.asGesturePointerType
import com.wynime.app.ui.foundation.input.trackActiveInputSource
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.video_player_release_to_cancel
import com.wynime.app.utils.fixToString
import com.wynime.app.utils.formatSpeedValue
import com.wynime.app.videoplayer.ui.ControllerVisibility
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.PlayerFullscreenState
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.BRIGHTNESS
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.FAST_BACKWARD
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.FAST_FORWARD
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.PAUSED_ONCE
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.PLAYBACK_SPEED
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.RESUMED_ONCE
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.SEEKING
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState.State.VOLUME
import com.wynime.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import com.wynime.app.videoplayer.ui.playerFocusHost
import com.wynime.app.videoplayer.ui.progress.PlayerProgressSliderState
import com.wynime.app.videoplayer.ui.toggle
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.AudioLevelController
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

@Stable
private fun renderTime(seconds: Int): String {
    return "${(seconds / 60).fixToString(2)}:${(seconds % 60).fixToString(2)}"
}

@Composable
fun rememberGestureIndicatorState(): GestureIndicatorState = remember { GestureIndicatorState() }

@Stable
class GestureIndicatorState {
    internal enum class State {
        PAUSED_ONCE,
        RESUMED_ONCE,
        VOLUME,
        BRIGHTNESS,
        SEEKING,
        FAST_FORWARD,
        FAST_BACKWARD,
        PLAYBACK_SPEED,
    }

    internal var visible: Boolean by mutableStateOf(false)
    internal var state: State? by mutableStateOf(null)
    internal var progressValue: Float by mutableFloatStateOf(0f)
    internal var deltaSeconds: Int by mutableIntStateOf(0)
    internal var seekCancelled: Boolean by mutableStateOf(false)
    internal var playbackSpeed: Float by mutableFloatStateOf(1f)
    private var counter: Int = 0

    private inline fun startShow(
        state: State,
        setup: () -> Unit = {},
    ): Int {
        val ticket = ++counter
        setup()
        this.state = state
        visible = true
        return ticket
    }

    private inline fun show(
        state: State,
        setup: () -> Unit = {},
        action: () -> Unit
    ) {
        val ticket = ++counter
        try {
            setup()
            this.state = state
            visible = true
            action()
        } finally {
            if (this.counter == ticket &&
                this.state == state
            ) {
                visible = false
            }
        }
    }

    private companion object {
        private const val LONG: Long = 700
        private const val SHORT: Long = 500
    }

    @UiThread
    suspend fun showPausedLong() {
        show(PAUSED_ONCE) {
            delay(LONG)
        }
    }

    @UiThread
    suspend fun showResumedLong() {
        show(RESUMED_ONCE) {
            delay(LONG)
        }
    }

    @UiThread
    suspend fun showVolumeRange(currentRatio: Float) {
        show(VOLUME, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showBrightnessRange(currentRatio: Float) {
        show(BRIGHTNESS, setup = { progressValue = currentRatio }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showPlaybackSpeed(speed: Float) {
        show(PLAYBACK_SPEED, setup = { playbackSpeed = speed }) {
            delay(SHORT)
        }
    }

    @UiThread
    suspend fun showSeeking(
        deltaSeconds: Int,
    ) {
        show(
            SEEKING,
            setup = {
                this.deltaSeconds = deltaSeconds
                seekCancelled = false
            },
        ) {
            delay(SHORT)
        }
    }

    @UiThread
    fun startSeekCancellation(): Int {
        return startShow(SEEKING) {
            seekCancelled = true
        }
    }

    @UiThread
    fun stopSeekCancellation(ticket: Int) {
        stopShow(ticket)
    }

    @UiThread
    fun startFastForward(speed: Float): Int {
        startShow(FAST_FORWARD, setup = { playbackSpeed = speed })
        return counter
    }

    @UiThread
    fun stopFastForward(ticket: Int) {
        stopShow(ticket)
    }

    @UiThread
    fun startFastBackward(): Int {
        startShow(FAST_BACKWARD, setup = { })
        return counter
    }

    @UiThread
    fun stopFastBackward(ticket: Int) {
        stopShow(ticket)
    }

    private fun stopShow(ticket: Int) {
        if (ticket == this.counter) {
            visible = false
        }
    }
}

@Immutable
internal data class GestureIndicatorPresentation(
    val state: GestureIndicatorState.State,
    val deltaSeconds: Int,
    val seekCancelled: Boolean,
)

internal fun gestureIndicatorPresentation(
    state: GestureIndicatorState,
    activeSwipeSeekerState: SwipeSeekerState?,
): GestureIndicatorPresentation? {
    if (!state.visible && activeSwipeSeekerState == null) return null
    val presentationState = if (activeSwipeSeekerState != null) SEEKING
    else state.state ?: return null
    return GestureIndicatorPresentation(
        state = presentationState,
        deltaSeconds = activeSwipeSeekerState?.deltaSeconds ?: state.deltaSeconds,
        seekCancelled = activeSwipeSeekerState?.isCancelled ?: state.seekCancelled,
    )
}

@Composable
fun GestureIndicator(
    state: GestureIndicatorState,
    swipeSeekerState: SwipeSeekerState? = null,
) {
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val activeSwipeSeekerState = swipeSeekerState?.takeIf { it.isSeeking }
    val presentation = gestureIndicatorPresentation(state, activeSwipeSeekerState)

    val retainedPresentation = remember { mutableStateOf<GestureIndicatorPresentation?>(null) }
    if (presentation != null) {
        SideEffect { retainedPresentation.value = presentation }
    }

    val currentPresentation = presentation ?: retainedPresentation.value

    WynimeAnimatedVisibility(
        visible = presentation != null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(tween(durationMillis = 500)),
        label = "SeekPositionIndicator",
    ) {
        currentPresentation ?: return@WynimeAnimatedVisibility
        Surface(
            Modifier.alpha(0.8f),
            color = colors.surface,
            shape = shape,
            shadowElevation = 1.dp,
            contentColor = colors.onSurface,
        ) {
            val iconSize = 36.dp
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)) {
                Row(
                    Modifier.background(Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(iconSize),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {

                    val progressIndicator: @Composable () -> Unit = remember(state, colors) {

                        {
                            LinearProgressIndicator(
                                progress = { state.progressValue },
                                modifier = Modifier.width(80.dp),
                                color = colors.primary,
                                trackColor = colors.onSurface.copy(alpha = 0.5f),
                                drawStopIndicator = {},
                            )
                        }
                    }

                    when (currentPresentation.state) {
                        RESUMED_ONCE -> {
                            Icon(
                                Icons.Rounded.PlayArrow, null,
                                Modifier.size(iconSize).background(Color.Transparent),
                            )
                        }

                        PAUSED_ONCE -> {
                            Icon(Icons.Rounded.Pause, null, Modifier.size(iconSize))
                        }

                        SEEKING -> {
                            Icon(
                                when {
                                    currentPresentation.seekCancelled -> Icons.Rounded.Close
                                    currentPresentation.deltaSeconds > 0 -> Icons.Rounded.FastForward
                                    else -> Icons.Rounded.FastRewind
                                },
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                text = if (currentPresentation.seekCancelled) {
                                    stringResource(Lang.video_player_release_to_cancel)
                                } else {
                                    renderTime(currentPresentation.deltaSeconds.absoluteValue)
                                },
                                maxLines = 1,
                            )
                        }

                        VOLUME -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp, null,
                                Modifier.size(iconSize),
                            )
                            progressIndicator()
                        }

                        BRIGHTNESS -> {
                            Icon(
                                when (state.progressValue) {
                                    in 0.67..1.0 -> Icons.Rounded.BrightnessHigh
                                    in 0.33..0.67 -> Icons.Rounded.BrightnessMedium
                                    else -> Icons.Rounded.BrightnessLow
                                },
                                null,
                                Modifier.size(iconSize),
                            )
                            progressIndicator()
                        }

                        FAST_FORWARD -> {
                            Icon(Icons.Rounded.FastForward, null, Modifier.size(iconSize))
                            Text("${state.playbackSpeed.formatSpeedValue()}x", maxLines = 1)
                        }

                        FAST_BACKWARD -> {
                            Icon(Icons.Rounded.FastRewind, null, Modifier.size(iconSize))
                        }

                        PLAYBACK_SPEED -> {
                            Icon(Icons.Rounded.FastForward, null, Modifier.size(iconSize))
                            Text("${state.playbackSpeed.formatSpeedValue()}x", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Stable
val Platform.mouseFamily: GestureFamily
    get() = when (this) {
        is Platform.Desktop -> GestureFamily.MOUSE
        is Platform.Android -> GestureFamily.TOUCH
    }

@Stable
fun gestureFamilyOf(activeInputSource: PointerType, fallback: GestureFamily): GestureFamily =
    when (activeInputSource.asGesturePointerType()) {
        PointerType.Touch -> GestureFamily.TOUCH
        PointerType.Mouse -> GestureFamily.MOUSE
        else -> fallback
    }

@Stable
fun hasPointerDevice(platform: Platform, hasSeenMouse: Boolean): Boolean =
    hasSeenMouse || platform.isDesktop()

@Immutable
enum class GestureFamily(
    val clickToPauseResume: Boolean,
    val clickToToggleController: Boolean,
    val doubleClickToFullscreen: Boolean,
    val doubleClickToPauseResume: Boolean,
    val autoHideController: Boolean,
) {
    TOUCH(
        clickToPauseResume = false,
        clickToToggleController = true,
        doubleClickToFullscreen = false,
        doubleClickToPauseResume = true,
        autoHideController = true,
    ),
    MOUSE(
        clickToPauseResume = true,
        clickToToggleController = false,
        doubleClickToFullscreen = true,
        doubleClickToPauseResume = false,
        autoHideController = false,
    )
}

val VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION = 3.seconds
val VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION = 3.seconds

private class SwipeSeekInteraction(
    private val controllerState: PlayerControllerState,
    private val seekerState: SwipeSeekerState,
    private val progressSliderState: PlayerProgressSliderState,
) {
    fun onStarted() {
        if (controllerState.visibility.bottomBar) {
            controllerState.setRequestInlineProgressSlider(this)
        } else {
            controllerState.setRequestProgressBar(this)
        }
    }

    fun onCancellationChanged(cancelled: Boolean) {
        if (cancelled) {
            progressSliderState.cancelPreview()
        } else {
            updatePreview()
        }
    }

    fun updatePreview() {
        if (seekerState.isCancelled) {
            progressSliderState.cancelPreview()
            return
        }
        if (progressSliderState.totalDurationMillis == 0L) return

        val previewPositionMillis =
            progressSliderState.currentPositionMillis + seekerState.deltaSeconds.times(1000)
        val offsetRatio = previewPositionMillis.toFloat() / progressSliderState.totalDurationMillis
        progressSliderState.previewPositionRatio(offsetRatio.coerceIn(0f, 1f))
    }

    fun onStopped(cancelled: Boolean) {
        cancelControllerRequest()
        if (cancelled) {
            progressSliderState.cancelPreview()
        } else {
            progressSliderState.finishPreview()
        }
    }

    fun dispose() {
        cancelControllerRequest()
    }

    private fun cancelControllerRequest() {
        controllerState.cancelRequestInlineProgressSlider(this)
        controllerState.cancelRequestProgressBarVisible(this)
    }
}

@Composable
private fun rememberSwipeSeekInteraction(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
): SwipeSeekInteraction {
    val interaction = remember(controllerState, seekerState, progressSliderState) {
        SwipeSeekInteraction(controllerState, seekerState, progressSliderState)
    }
    DisposableEffect(interaction) {
        onDispose(interaction::dispose)
    }
    return interaction
}

@Composable
fun PlayerGestureHost(
    controllerState: PlayerControllerState,
    seekerState: SwipeSeekerState,
    progressSliderState: PlayerProgressSliderState,
    indicatorState: GestureIndicatorState,
    fastSkipState: FastSkipState?,
    playerState: MediampPlayer,
    enableSwipeToSeek: Boolean,
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
    family: GestureFamily = gestureFamilyOf(
        LocalActiveInputSource.current.current,
        LocalPlatform.current.mouseFamily,
    ),
    onTogglePauseResume: () -> Unit = {},
    onTogglePlayerStats: () -> Unit = {},
) {
    val onTogglePauseResumeState by rememberUpdatedState(onTogglePauseResume)
    val isFullscreen = fullscreenState.isFullscreen

    val inputSourceState = LocalActiveInputSource.current

    val swipeGesturesEnabled = family == GestureFamily.TOUCH
    BoxWithConstraints(Modifier.trackActiveInputSource(inputSourceState)) {
        Row(
            Modifier.align(Alignment.TopCenter)
                .systemGesturesPadding()
                .padding(top = 16.dp),
        ) {
            GestureIndicator(indicatorState, swipeSeekerState = seekerState)
        }
        val maxHeight = maxHeight
        val adjustingVolumeOrBrightness =
            indicatorState.visible && (indicatorState.state == VOLUME || indicatorState.state == BRIGHTNESS)
        val adjustingForwardOrBackward =
            indicatorState.visible && (indicatorState.state == FAST_FORWARD || indicatorState.state == FAST_BACKWARD)

        val indicatorTasker = rememberUiMonoTasker()
        val audioLevelController = playerState.features[AudioLevelController]

        val useMediaAudioController = LocalPlatform.current.isDesktop()
        val playerFocusState = controllerState.focusState

        val keyboardModifier = modifier
            .testTag("VideoGestureHost")
            .playerKeyboardShortcuts(
                seekerState = seekerState,
                fastSkipState = fastSkipState,
                currentPlaybackSpeed = playbackSpeedControllerState?.currentSpeed,
                playbackSpeedRange = playbackSpeedControllerState?.speedRange
                    ?: PlaybackSpeedControllerState.DEFAULT_SPEED_RANGE,
                onPlaybackSpeedChanged = {
                    playbackSpeedControllerState?.commitSpeed(it)
                    indicatorTasker.launch { indicatorState.showPlaybackSpeed(it) }
                },
                volumeEnabled = !useMediaAudioController || audioLevelController != null,
                onVolumeUp = { fineAdjustment ->
                    if (useMediaAudioController) {
                        checkNotNull(audioLevelController)
                        if (fineAdjustment) audioLevelController.volumeUp(0.01f) else audioLevelController.volumeUp()
                        audioLevelController.setMute(false)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                        }
                    } else {
                        audioController.increaseLevel(if (fineAdjustment) audioController.levelStep else 0.10f)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioController.level)
                        }
                    }
                },
                onVolumeDown = { fineAdjustment ->
                    if (useMediaAudioController) {
                        checkNotNull(audioLevelController)
                        if (fineAdjustment) audioLevelController.volumeDown(0.01f) else audioLevelController.volumeDown()
                        audioLevelController.setMute(false)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                        }
                    } else {
                        audioController.decreaseLevel(if (fineAdjustment) audioController.levelStep else 0.10f)
                        indicatorTasker.launch {
                            indicatorState.showVolumeRange(audioController.level)
                        }
                    }
                },
                onTogglePauseResume = onTogglePauseResumeState,
                onToggleFullscreen = remember(fullscreenState) { { fullscreenState.toggle() } },
                onTogglePlayerStats = onTogglePlayerStats,
            )
            .playerFocusHost(playerFocusState, isFullscreen)

        if (family.autoHideController) {
            LaunchedEffect(controllerState.visibility, controllerState.alwaysOn) {
                if (controllerState.alwaysOn) return@LaunchedEffect
                if (controllerState.visibility.bottomBar) {
                    delay(VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION)
                    controllerState.toggleFullVisible(false)
                }
            }
        }

        if (hasPointerDevice(LocalPlatform.current, inputSourceState.hasSeenMouse)) {

            LaunchedEffect(controllerState) {
                snapshotFlow { controllerState.alwaysOn }.collectLatest { alwaysOn ->
                    if (alwaysOn) return@collectLatest
                    snapshotFlow { controllerState.visibility != ControllerVisibility.Invisible }.collectLatest {
                        if (!it) {
                            delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                            controllerState.toggleFullVisible(false)
                        }
                    }
                }
            }
        }

        @Composable
        fun Modifier.combineClickableWithFamilyGesture() = this then
                combinedClickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = remember(family, playerFocusState, inputSourceState) {
                        {

                            val tapFamily = gestureFamilyOf(inputSourceState.latest, family)
                            if (tapFamily.clickToPauseResume) {
                                onTogglePauseResumeState()
                            }
                            if (tapFamily.clickToToggleController) {
                                controllerState.toggleFullVisible()
                            }
                            playerFocusState.requestPlayerFocus()
                        }
                    },
                    onDoubleClick = remember(family, fullscreenState, playerFocusState, inputSourceState) {
                        {
                            val tapFamily = gestureFamilyOf(inputSourceState.latest, family)
                            if (tapFamily.doubleClickToFullscreen) {
                                fullscreenState.toggle()
                            }
                            if (tapFamily.doubleClickToPauseResume) {
                                onTogglePauseResumeState()
                            }
                            playerFocusState.requestPlayerFocus()
                        }
                    },
                )

        val mouseMoveTasker = rememberUiMonoTasker()
        Box(
            keyboardModifier
                .combineClickableWithFamilyGesture()
                .ifThen(enableSwipeToSeek) {
                    val swipeSeekInteraction = rememberSwipeSeekInteraction(
                        controllerState,
                        seekerState,
                        progressSliderState,
                    )
                    swipeToSeek(
                        seekerState,
                        Orientation.Horizontal,

                        enabled = swipeGesturesEnabled && !adjustingVolumeOrBrightness,
                        onDragStarted = {
                            swipeSeekInteraction.onStarted()
                        },
                        onDragStopped = { _, cancelled ->
                            swipeSeekInteraction.onStopped(cancelled)
                        },
                        onCancellationChanged = { cancelled ->
                            swipeSeekInteraction.onCancellationChanged(cancelled)
                        },
                    ) {
                        swipeSeekInteraction.updatePreview()
                    }
                }
                .onPointerEventMultiplatform(PointerEventType.Move) { event ->
                    if (event.changes.firstOrNull()?.type == PointerType.Mouse) {
                        playerFocusState.requestPlayerFocus()
                    }
                }

                .onPointerEventMultiplatform(PointerEventType.Move) { event ->
                    if (event.changes.firstOrNull()?.type == PointerType.Mouse) {
                        controllerState.toggleFullVisible(true)
                        mouseMoveTasker.launch {
                            delay(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION)
                            controllerState.toggleFullVisible(false)
                        }
                    }
                }

                .ifThen(audioLevelController != null) {
                    if (audioLevelController == null) return@ifThen this
                    onPointerEventMultiplatform(PointerEventType.Scroll) { event ->
                        event.changes.firstOrNull()?.scrollDelta?.y?.run {
                            audioLevelController.setMute(false)
                            if (this < 0) audioLevelController.volumeUp()
                            else if (this > 0) audioLevelController.volumeDown()

                            indicatorTasker.launch {
                                indicatorState.showVolumeRange(audioLevelController.volume.value / audioLevelController.maxVolume)
                            }
                        }
                    }
                }

                .focusable()
                .fillMaxSize(),
        ) {
            Row(

                Modifier.matchParentSize()
                    .systemGesturesPadding()

                    .ifNotNullThen(fastSkipState) {
                        longPressFastSkip(it, SkipDirection.FORWARD, requiredPointerType = PointerType.Touch)
                    },
            ) {
                Box(
                    Modifier

                        .ifThen(brightnessController !== NoOpLevelController) {
                            swipeLevelControlWithIndicator(
                                brightnessController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                indicatorState,
                                enabled = swipeGesturesEnabled && !seekerState.isSeeking && !adjustingForwardOrBackward,
                                step = 0.01f,
                                setup = {
                                    indicatorState.state = BRIGHTNESS
                                },
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )

                Box(
                    Modifier
                        .swipeToFullscreen(
                            enabled = swipeGesturesEnabled && !seekerState.isSeeking && !adjustingVolumeOrBrightness &&
                                    !adjustingForwardOrBackward,

                            onEnterFullscreen = { fullscreenState.request(true) },
                            onExitFullscreen = { fullscreenState.request(false) },
                        )
                        .weight(1f)
                        .fillMaxHeight(),
                )

                Box(
                    Modifier
                        .ifThen(audioController !== NoOpLevelController) {
                            swipeLevelControlWithIndicator(
                                audioController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                Orientation.Vertical,
                                indicatorState,
                                enabled = swipeGesturesEnabled && !seekerState.isSeeking && !adjustingForwardOrBackward,
                                step = 0.05f,
                                setup = {
                                    indicatorState.state = VOLUME
                                },
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        if (family.clickToToggleController && isFullscreen) {

            Box(
                Modifier.fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.systemGestures)
                    .combineClickableWithFamilyGesture(),
            )
        }
    }
}
