package com.wynime.app.videoplayer.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import com.wynime.app.ui.foundation.SteppedSlider
import com.wynime.app.ui.foundation.dialogs.PlatformPopupProperties
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.theme.WynimeTheme
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.video_player_cancel
import com.wynime.app.ui.lang.video_player_mute
import com.wynime.app.ui.lang.video_player_next_episode
import com.wynime.app.ui.lang.video_player_pause
import com.wynime.app.ui.lang.video_player_play
import com.wynime.app.ui.lang.video_player_select_episode
import com.wynime.app.ui.lang.video_player_skip_op_ed
import com.wynime.app.ui.lang.video_player_speed
import com.wynime.app.ui.lang.video_player_volume
import com.wynime.app.utils.formatSpeedValue
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.PlayerFullscreenState
import com.wynime.app.videoplayer.ui.VideoAspectRatioControllerState
import com.wynime.app.videoplayer.ui.keepLayoutWhenHidden
import com.wynime.app.videoplayer.ui.renderAspectRatioMode
import com.wynime.app.videoplayer.ui.toggle
import com.wynime.app.videoplayer.ui.top.needWorkaroundForFocusManager
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

const val TAG_SELECT_EPISODE_ICON_BUTTON = "SelectEpisodeIconButton"
const val TAG_SPEED_SWITCHER_TEXT_BUTTON = "SpeedSwitcherTextButton"
const val TAG_SPEED_SWITCHER_DROPDOWN_MENU = "SpeedSwitcherDropdownMenu"
const val TAG_SPEED_SWITCHER_SLIDER = "SpeedSwitcherSlider"
const val TAG_SPEED_SWITCHER_VALUE_INDICATOR = "SpeedSwitcherValueIndicator"
const val TAG_VIDEO_ASPECT_RATIO_SELECTOR_TEXT_BUTTON = "VideoAspectRatioTextButton"
const val TAG_VIDEO_ASPECT_RATIO_SELECTOR_DROPDOWN_MENU = "VideoAspectRatioDropdownMenu"

const val TAG_FULL_SCREEN_BUTTON = "FullScreenButton"

@Stable
object PlayerControllerDefaults {

    @Composable
    fun PlaybackIcon(
        isPlaying: () -> Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier,
        ) {
            if (isPlaying()) {
                Icon(Icons.Rounded.Pause, contentDescription = stringResource(Lang.video_player_pause), Modifier.size(36.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(Lang.video_player_play), Modifier.size(36.dp))
            }
        }
    }

    @Composable
    fun AudioIcon(
        volume: Float,
        isMute: Boolean,
        maxValue: Float,
        onClick: () -> Unit,
        onchange: (Float) -> Unit,
        controllerState: PlayerControllerState,
        modifier: Modifier = Modifier,
    ) {
        val hoverInteraction = remember { MutableInteractionSource() }
        val isHovered by hoverInteraction.collectIsHoveredAsState()
        val audioIconRequester = remember { Any() }

        LaunchedEffect(true) {
            snapshotFlow { isHovered }.collect {
                controllerState.setRequestAlwaysOn(audioIconRequester, isHovered)
            }
        }
        Box(
            modifier = modifier.hoverable(hoverInteraction),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val iconButton = @Composable {
                IconButton(
                    onClick = onClick,
                ) {
                    when {
                        isMute -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = stringResource(Lang.video_player_mute),
                            )
                        }

                        volume < 0.33f -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeMute,
                                contentDescription = stringResource(Lang.video_player_volume),
                            )
                        }

                        volume < 0.66f -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeDown,
                                contentDescription = stringResource(Lang.video_player_volume),
                            )
                        }

                        else -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(Lang.video_player_volume),
                            )
                        }
                    }
                }
            }

            iconButton()

            Popup(
                alignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .hoverable(hoverInteraction)
                        .clip(shape = CircleShape),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedVisibility(
                            visible = isHovered && !isMute,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = volume.times(100).roundToInt().toString(),
                                    modifier = Modifier.padding(8.dp),
                                )
                                val colors = SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface,
                                )
                                VerticalSlider(
                                    value = volume,
                                    onValueChange = onchange,
                                    modifier = Modifier.width(96.dp),
                                    thumb = {},
                                    colors = colors,
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            colors = colors,
                                            enabled = true,
                                            sliderState = sliderState,
                                            thumbTrackGapSize = 0.dp,
                                        )
                                    },
                                    valueRange = 0f..maxValue,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isHovered && !isMute,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            iconButton()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NextEpisodeIcon(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick,
            modifier,
        ) {
            Icon(Icons.Rounded.SkipNext, stringResource(Lang.video_player_next_episode), Modifier.size(36.dp))
        }
    }

    @Composable
    fun SelectEpisodeIcon(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        TextButton(
            onClick,
            modifier.testTag(TAG_SELECT_EPISODE_ICON_BUTTON),
            colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current,
            ),
        ) {
            Text(stringResource(Lang.video_player_select_episode))
        }
    }

    @Composable
    fun FullscreenIcon(
        fullscreenState: PlayerFullscreenState,
        modifier: Modifier = Modifier,
    ) {
        val isFullscreen = fullscreenState.isFullscreen
        val focusManager by rememberUpdatedState(LocalFocusManager.current)
        IconButton(
            onClick = remember(fullscreenState) { { fullscreenState.toggle() } },
            modifier.ifThen(needWorkaroundForFocusManager) {
                onFocusEvent {
                    if (it.hasFocus) {
                        focusManager.clearFocus()
                    }
                }
            }.testTag(TAG_FULL_SCREEN_BUTTON),
        ) {
            if (isFullscreen) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", Modifier.size(32.dp))
            } else {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "Enter Fullscreen", Modifier.size(32.dp))
            }
        }
    }

    @Composable
    fun SpeedSwitcher(
        state: PlaybackSpeedControllerState,
        modifier: Modifier = Modifier,
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        SpeedSwitcher(
            currentSpeed = state.currentSpeed,
            speedRange = state.speedRange,
            onPreviewSpeed = state::previewSpeed,
            onCommitSpeed = state::commitSpeed,
            modifier = modifier,
            onExpandedChanged = onExpandedChanged,
        )
    }

    @Composable
    fun SpeedSwitcher(
        currentSpeed: Float,
        speedRange: ClosedFloatingPointRange<Float>,
        onPreviewSpeed: (Float) -> Unit,
        onCommitSpeed: (Float) -> Unit,
        modifier: Modifier = Modifier,
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        fun setExpanded(value: Boolean) {
            expanded = value
            onExpandedChanged(value)
        }

        Box(modifier, contentAlignment = Alignment.Center) {
            SpeedSwitcherButton(
                speed = currentSpeed,
                onClick = { setExpanded(true) },
            )

            if (expanded) {
                SpeedSliderPopup(
                    currentSpeed,
                    speedRange,
                    onPreviewSpeed,
                    onCommitSpeed,
                    onDismissRequest = { setExpanded(false) },
                )
            }
        }
    }

    @Composable
    private fun SpeedSwitcherButton(
        speed: Float,
        onClick: () -> Unit,
    ) {
        val speedText = stringResource(Lang.video_player_speed)
        TextButton(
            onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
            modifier = Modifier.testTag(TAG_SPEED_SWITCHER_TEXT_BUTTON),
        ) {
            Text(remember(speed, speedText) { if (speed == 1.0f) speedText else """${speed.formatSpeedValue()}x""" })
        }
    }

    @Composable
    private fun SpeedSliderPopup(
        currentSpeed: Float,
        speedRange: ClosedFloatingPointRange<Float>,
        onPreviewSpeed: (Float) -> Unit,
        onCommitSpeed: (Float) -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        Popup(
            popupPositionProvider = rememberAboveAnchorWithinWindowPositionProvider(spacing = 8.dp),
            onDismissRequest = onDismissRequest,
            properties = PlatformPopupProperties(focusable = true, clippingEnabled = false),
        ) {
            WynimeTheme(darkModeOverride = DarkMode.DARK) {
                Surface(
                    modifier = Modifier
                        .testTag(TAG_SPEED_SWITCHER_DROPDOWN_MENU)
                        .width(280.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                ) {
                    SteppedSlider(
                        value = currentSpeed,
                        onValueChange = onPreviewSpeed,
                        onValueChangeFinished = onCommitSpeed,
                        valueRange = speedRange,
                        valueIndicator = {
                            Text(
                                it.formatSpeedValue(),
                                Modifier.testTag(TAG_SPEED_SWITCHER_VALUE_INDICATOR),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier
                            .testTag(TAG_SPEED_SWITCHER_SLIDER)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun rememberAboveAnchorWithinWindowPositionProvider(spacing: Dp): PopupPositionProvider {
        val spacingPx = with(LocalDensity.current) { spacing.roundToPx() }
        return remember(spacingPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                    val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(0, maxX)
                    val above = anchorBounds.top - popupContentSize.height - spacingPx
                    val y = if (above >= 0) above else anchorBounds.bottom + spacingPx
                    return IntOffset(x, y)
                }
            }
        }
    }

    @Composable
    fun VideoAspectRatioSelector(
        videoAspectRatioControllerState: VideoAspectRatioControllerState,
        modifier: Modifier = Modifier,
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        return OptionsSwitcher(
            value = videoAspectRatioControllerState.currentMode,
            onValueChange = { videoAspectRatioControllerState.setMode(it) },
            optionsProvider = { VideoAspectRatioControllerState.Entries },
            renderValue = { Text(renderAspectRatioMode(it)) },
            renderValueExposed = { Text(renderAspectRatioMode(it)) },
            modifier,
            properties = PlatformPopupProperties(
                clippingEnabled = false,
            ),
            textButtonTestTag = TAG_VIDEO_ASPECT_RATIO_SELECTOR_TEXT_BUTTON,
            dropdownMenuTestTag = TAG_VIDEO_ASPECT_RATIO_SELECTOR_DROPDOWN_MENU,
            onExpandedChanged = onExpandedChanged,
        )
    }

    @Composable
    fun <T> OptionsSwitcher(
        value: T,
        onValueChange: (T) -> Unit,
        optionsProvider: () -> List<T>,
        renderValue: @Composable (T) -> Unit,
        renderValueExposed: @Composable (T) -> Unit = renderValue,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        properties: PopupProperties = PopupProperties(),
        textButtonTestTag: String = "textButton",
        dropdownMenuTestTag: String = "dropDownMenu",
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        Box(modifier, contentAlignment = Alignment.Center) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(true) {
                snapshotFlow { expanded }.collect {
                    onExpandedChanged(expanded)
                }
            }
            TextButton(
                { expanded = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalContentColor.current,
                ),
                enabled = enabled,
                modifier = Modifier.testTag(textButtonTestTag),
            ) {
                renderValueExposed(value)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                properties = properties,
                modifier = Modifier.testTag(dropdownMenuTestTag),
            ) {
                val options = remember(optionsProvider) { optionsProvider() }
                for (option in options) {
                    DropdownMenuItem(
                        text = {
                            val color = if (value == option) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                            CompositionLocalProvider(LocalContentColor provides color) {
                                renderValue(option)
                            }
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option)
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun MediaProgressSlider(
        progressSliderState: PlayerProgressSliderState,
        cacheProgressInfoFlow: Flow<MediaCacheProgressInfo>,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        showPreviewTimeTextOnThumb: Boolean = true,
        framePreview: MediaProgressFramePreviewState? = null,
        showFramePreviewInPopup: Boolean = true,
        touchSeekState: TouchSeekState? = null,
    ) {
        val cacheProgressInfo by cacheProgressInfoFlow.collectAsStateWithLifecycle(null)
        MediaProgressSlider(
            progressSliderState, { cacheProgressInfo },
            enabled = enabled,
            showPreviewTimeTextOnThumb = showPreviewTimeTextOnThumb,
            framePreview = framePreview,
            showFramePreviewInPopup = showFramePreviewInPopup,
            touchSeekState = touchSeekState,
            modifier = modifier,
        )
    }

    @Composable
    fun LeftBottomTips(
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        WynimeTheme(darkModeOverride = DarkMode.DARK) {
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Lang.video_player_skip_op_ed))
                        TextButton(onClick = onClick) {
                            Text(stringResource(Lang.video_player_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerControllerBar(
    startActions: @Composable RowScope.() -> Unit,
    progressIndicator: @Composable RowScope.() -> Unit,
    progressSlider: @Composable RowScope.() -> Unit,
    endActions: @Composable RowScope.() -> Unit,
    expanded: Boolean,
    sliderOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, null, onClick = {})
            .padding(
                horizontal = if (expanded) 8.dp else 4.dp,
                vertical = if (expanded) 4.dp else 2.dp,
            ),
    ) {
        Column {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                Row(
                    Modifier
                        .keepLayoutWhenHidden(sliderOnly)
                        .padding(start = if (expanded) 8.dp else 4.dp)
                        .padding(vertical = if (expanded) 4.dp else 2.dp),
                ) {
                    progressIndicator()
                }
                if (expanded) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        progressSlider()
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 4.dp),
        ) {

            Row(
                Modifier.keepLayoutWhenHidden(sliderOnly),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                startActions()
            }

            if (!expanded) {
                Row(
                    Modifier.weight(1f).keepLayoutWhenHidden(sliderOnly),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    progressSlider()
                }
            }

            Row(
                Modifier.keepLayoutWhenHidden(sliderOnly),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                endActions()
            }
        }
    }
}
