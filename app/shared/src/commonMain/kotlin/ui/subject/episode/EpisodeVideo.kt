package com.wynime.app.ui.subject.episode

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.MediaCacheProgressInfo
import com.wynime.app.domain.media.player.staticMediaCacheProgressState
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.tools.rememberUiMonoTasker
import com.wynime.app.ui.episode.share.MediaShareData
import com.wynime.app.ui.foundation.LocalIsPreviewing
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.TextWithBorder
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.effects.cursorVisibility
import com.wynime.app.ui.foundation.icons.WynimeIcons
import com.wynime.app.ui.foundation.icons.Forward80
import com.wynime.app.ui.foundation.icons.Forward85
import com.wynime.app.ui.foundation.icons.Forward90
import com.wynime.app.ui.foundation.icons.RightPanelClose
import com.wynime.app.ui.foundation.icons.RightPanelOpen
import com.wynime.app.ui.foundation.icons.SubtitleGear
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.LocalActiveInputSource
import com.wynime.app.ui.foundation.interaction.WindowDragArea
import com.wynime.app.ui.foundation.rememberDebugSettingsViewModel
import com.wynime.app.ui.foundation.theme.WynimeTheme
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.always_on_top
import com.wynime.app.ui.lang.subject_episode_cache
import com.wynime.app.ui.lang.subject_episode_collapse_sidebar
import com.wynime.app.ui.lang.subject_episode_expand_sidebar
import com.wynime.app.ui.lang.subject_episode_external_links
import com.wynime.app.ui.lang.subject_episode_fast_forward_seconds
import com.wynime.app.ui.lang.subject_episode_more_options
import com.wynime.app.ui.lang.subject_episode_preview_mode
import com.wynime.app.ui.lang.subject_episode_select_media_source
import com.wynime.app.ui.lang.video_player_stats_title_hide
import com.wynime.app.ui.lang.video_player_stats_title_show
import com.wynime.app.ui.lang.video_player_video_enhancement
import com.wynime.app.ui.mediafetch.rememberTestMediaSelectorState
import com.wynime.app.ui.mediafetch.request.TestMediaFetchRequest
import com.wynime.app.ui.subject.episode.details.components.ShareEpisodeDropdown
import com.wynime.app.ui.subject.episode.details.components.VideoEnhancementDropdown
import com.wynime.app.ui.subject.episode.video.DEFAULT_OP_ED_SKIP_DURATION
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import com.wynime.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import com.wynime.app.ui.subject.episode.video.components.SideSheets
import com.wynime.app.ui.subject.episode.video.components.rememberStatusBarHeightAsState
import com.wynime.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator
import com.wynime.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import com.wynime.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import com.wynime.app.ui.subject.episode.video.sidesheet.rememberTestEpisodeSelectorState
import com.wynime.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import com.wynime.app.videoplayer.ui.ControllerVisibility
import com.wynime.app.videoplayer.ui.MutablePlayerFullscreenState
import com.wynime.app.videoplayer.ui.NoOpVideoAspectRatio
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.PlayerFullscreenState
import com.wynime.app.videoplayer.ui.PlayerStatsOverlay
import com.wynime.app.videoplayer.ui.VideoAspectRatioControllerState
import com.wynime.app.videoplayer.ui.VideoPlayer
import com.wynime.app.videoplayer.ui.VideoScaffold
import com.wynime.app.videoplayer.ui.VideoSideSheetsController
import com.wynime.app.videoplayer.ui.gesture.GestureFamily
import com.wynime.app.videoplayer.ui.gesture.GestureIndicatorState
import com.wynime.app.videoplayer.ui.gesture.GestureLock
import com.wynime.app.videoplayer.ui.gesture.LevelController
import com.wynime.app.videoplayer.ui.gesture.LockableVideoGestureHost
import com.wynime.app.videoplayer.ui.gesture.NoOpLevelController
import com.wynime.app.videoplayer.ui.gesture.ScreenshotButton
import com.wynime.app.videoplayer.ui.gesture.SwipeSeekerConfig
import com.wynime.app.videoplayer.ui.gesture.gestureFamilyOf
import com.wynime.app.videoplayer.ui.gesture.hasPointerDevice
import com.wynime.app.videoplayer.ui.gesture.mouseFamily
import com.wynime.app.videoplayer.ui.gesture.rememberGestureIndicatorState
import com.wynime.app.videoplayer.ui.gesture.rememberSwipeSeekerState
import com.wynime.app.videoplayer.ui.hasPageAsState
import com.wynime.app.videoplayer.ui.progress.AudioSwitcher
import com.wynime.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import com.wynime.app.videoplayer.ui.progress.MediaProgressIndicatorText
import com.wynime.app.videoplayer.ui.progress.MediaProgressSliderDefaults
import com.wynime.app.videoplayer.ui.progress.PlayerControllerBar
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults.SpeedSwitcher
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults.VideoAspectRatioSelector
import com.wynime.app.videoplayer.ui.progress.PlayerProgressSliderState
import com.wynime.app.videoplayer.ui.progress.ProgressSliderCenteredPreviewFrame
import com.wynime.app.videoplayer.ui.progress.SubtitleSwitcher
import com.wynime.app.videoplayer.ui.progress.TouchSeekState
import com.wynime.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import com.wynime.app.videoplayer.ui.rememberAlwaysOnRequester
import com.wynime.app.videoplayer.ui.rememberPlayerStatsState
import com.wynime.app.videoplayer.ui.rememberVideoControllerState
import com.wynime.app.videoplayer.ui.rememberVideoSideSheetsController
import com.wynime.app.videoplayer.ui.top.PlayerTopBar
import com.wynime.app.videoplayer.ui.top.SystemTime
import com.wynime.app.videoplayer.videoenhancement.VideoEnhancementController
import com.wynime.app.videoplayer.videoenhancement.VideoEnhancementMode
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.isAndroid
import com.wynime.utils.platform.isDesktop
import com.wynime.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.togglePlayWhenReady
import kotlin.time.Duration

internal const val TAG_EPISODE_VIDEO_TOP_BAR = "EpisodeVideoTopBar"

internal const val TAG_SHOW_MEDIA_SELECTOR = "ShowMediaSelector"
internal const val TAG_VIDEO_ENHANCEMENT = "VideoEnhancement"
internal const val TAG_SHOW_SETTINGS = "ShowSettings"
internal const val TAG_COLLAPSE_SIDEBAR = "collapseSidebar"
internal const val TAG_MEDIA_SELECTOR_SHEET = "MediaSelectorSheet"
internal const val TAG_EPISODE_SELECTOR_SHEET = "EpisodeSelectorSheet"

@Composable
fun EpisodeVideoImpl(
    playerState: MediampPlayer,
    expanded: Boolean,
    hasNextEpisode: Boolean,
    onClickNextEpisode: () -> Unit,
    playerControllerState: PlayerControllerState,
    opEdSkipDuration: Duration = DEFAULT_OP_ED_SKIP_DURATION,
    onClickSkipOpEd: (currentPositionMillis: Long) -> Unit = {
        playerState.skip(opEdSkipDuration.inWholeMilliseconds)
    },
    title: @Composable () -> Unit,
    videoLoadingStateFlow: Flow<VideoLoadingState>,
    fullscreenState: PlayerFullscreenState,
    alwaysOnTop: Boolean = false,
    onToggleAlwaysOnTop: (() -> Unit)? = null,
    onClickScreenshot: () -> Unit,
    detachedProgressSlider: @Composable () -> Unit,
    sidebarVisible: Boolean,
    onToggleSidebar: (isCollapsed: Boolean) -> Unit,
    progressSliderState: PlayerProgressSliderState,
    cacheProgressInfoFlow: Flow<MediaCacheProgressInfo>,
    framePreview: MediaProgressFramePreviewState? = null,
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    videoAspectRatioControllerState: VideoAspectRatioControllerState?,
    videoEnhancement: VideoEnhancementController? = null,
    leftBottomTips: @Composable () -> Unit,
    fullscreenSwitchButton: @Composable () -> Unit,
    sideSheets: @Composable (controller: VideoSideSheetsController<EpisodeVideoSideSheetPage>) -> Unit,
    shareData: MediaShareData,
    onClickCache: () -> Unit,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    gestureFamily: GestureFamily = gestureFamilyOf(
        LocalActiveInputSource.current.current,
        LocalPlatform.current.mouseFamily,
    ),
    fastForwardSpeed: Float = 3f,
    contentWindowInsets: WindowInsets = WindowInsets(0.dp),
) {

    var isLocked by remember { mutableStateOf(false) }
    var showPlayerStats by remember { mutableStateOf(false) }
    val playerStats by rememberPlayerStatsState(playerState)
    val sheetsController = rememberVideoSideSheetsController<EpisodeVideoSideSheetPage>()
    val anySideSheetVisible by sheetsController.hasPageAsState()
    val previewModeText = stringResource(Lang.subject_episode_preview_mode)

    val videoInteractionSource = remember { MutableInteractionSource() }
    val isVideoHovered by videoInteractionSource.collectIsHoveredAsState()
    val showCursor by remember(playerControllerState) {
        derivedStateOf {
            !isVideoHovered || (playerControllerState.visibility.bottomBar
                    || playerControllerState.visibility.detachedSlider
                    || anySideSheetVisible)
        }
    }
    val indicatorState = rememberGestureIndicatorState()
    val swipeSeekerConfig = SwipeSeekerConfig.Default
    val touchSeekState = rememberPlayerTouchSeekState(
        controllerState = playerControllerState,
        indicatorState = indicatorState,
        swipeSeekerConfig = swipeSeekerConfig,
    )

    WynimeTheme(darkModeOverride = DarkMode.DARK) {
        val progressSliderColors = MediaProgressSliderDefaults.colors()
        VideoScaffold(
            expanded = expanded,
            modifier = modifier
                .hoverable(videoInteractionSource)
                .cursorVisibility(showCursor),
            contentWindowInsets = contentWindowInsets,
            maintainAspectRatio = maintainAspectRatio,
            controllerState = playerControllerState,
            gestureLocked = isLocked,
            topBar = {
                WindowDragArea {
                    PlayerTopBar(
                        Modifier.testTag(TAG_EPISODE_VIDEO_TOP_BAR),
                        title = if (expanded) {
                            { title() }
                        } else {
                            null
                        },
                        actions = {
                            EpisodeVideoTopBarActions(
                                playerState = playerState,
                                expanded = expanded,
                                opEdSkipDuration = opEdSkipDuration,
                                onClickSkipOpEd = onClickSkipOpEd,
                                sheetsController = sheetsController,
                                shareData = shareData,
                                onClickCache = onClickCache,
                                playerControllerState = playerControllerState,
                                videoEnhancement = videoEnhancement,
                                sidebarVisible = sidebarVisible,
                                onToggleSidebar = onToggleSidebar,
                                playerStatsVisible = showPlayerStats,
                                onTogglePlayerStats = { showPlayerStats = !showPlayerStats },
                                alwaysOnTop = alwaysOnTop,
                                onToggleAlwaysOnTop = onToggleAlwaysOnTop,
                            )
                        },

                        windowInsets = WindowInsets(0.dp),
                    )
                }
            },
            centerOverlay = if (expanded && LocalPlatform.current.isMobile()) {
                { SystemTime() }
            } else {
                {}
            },
            video = {
                if (LocalIsPreviewing.current) {
                    Text(previewModeText)
                } else {

                    val statusBarHeight by rememberStatusBarHeightAsState()

                    VideoPlayer(
                        playerState,
                        Modifier
                            .ifThen(statusBarHeight != 0.dp) {
                                offset(x = -statusBarHeight / 2, y = 0.dp)
                            }
                            .onSizeChanged {
                                videoEnhancement?.setViewportSize(it.width, it.height)
                            }
                            .matchParentSize(),
                    )
                }
            },
            gestureHost = {
                val swipeSeekerState = rememberSwipeSeekerState(
                    constraints.maxWidth,
                    swipeSeekerConfig,
                ) {
                    playerState.skip(it * 1000L)
                }
                val videoPropertiesState by playerState.mediaProperties.collectAsState(null)
                val enableSwipeToSeek by remember {
                    derivedStateOf {
                        videoPropertiesState?.let { it.durationMillis != 0L } == true
                    }
                }

                val indicatorTasker = rememberUiMonoTasker()
                LockableVideoGestureHost(
                    playerControllerState,
                    swipeSeekerState,
                    progressSliderState,
                    playerState,
                    locked = isLocked,
                    enableSwipeToSeek = enableSwipeToSeek,
                    audioController = audioController,
                    brightnessController = brightnessController,
                    playbackSpeedControllerState = playbackSpeedControllerState,
                    fullscreenState = fullscreenState,
                    modifier = Modifier,
                    onTogglePauseResume = {
                        if (playerState.state.value.playWhenReady) {
                            indicatorTasker.launch {
                                indicatorState.showPausedLong()
                            }
                        } else {
                            indicatorTasker.launch {
                                indicatorState.showResumedLong()
                            }
                        }
                        playerState.togglePlayWhenReady()
                    },
                    onTogglePlayerStats = {
                        showPlayerStats = !showPlayerStats
                    },
                    family = gestureFamily,
                    gestureIndicatorState = indicatorState,
                    fastForwardSpeed = fastForwardSpeed,
                )
            },
            playerStatsOverlay = {
                if (showPlayerStats) {
                    PlayerStatsOverlay(playerStats)
                }
            },
            floatingMessage = {
                Column {
                    val videoLoadingState by videoLoadingStateFlow.collectAsStateWithLifecycle(VideoLoadingState.Initial)
                    EpisodeVideoLoadingIndicator(
                        playerState,
                        videoLoadingState,
                        optimizeForFullscreen = expanded,
                    )
                    val debugViewModel = rememberDebugSettingsViewModel()
                    @OptIn(TestOnly::class)
                    if (debugViewModel.isAppInDebugMode && debugViewModel.showControllerAlwaysOnRequesters) {
                        TextWithBorder(
                            "Always on requesters: \n" +
                                    playerControllerState.getAlwaysOnRequesters().joinToString("\n"),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        TextWithBorder(
                            "ControllerVisibility: \n" + playerControllerState.visibility,
                            style = MaterialTheme.typography.labelLarge,
                        )

                        TextWithBorder(
                            "expanded: $expanded",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            },
            framePreviewOverlay = {
                if (!expanded) {
                    ProgressSliderCenteredPreviewFrame(
                        frame = framePreview?.frame,
                        borderColor = progressSliderColors.previewTimeBackgroundColor,
                    )
                }
            },
            rhsButtons = {
                if (expanded && (LocalPlatform.current.isDesktop() || LocalPlatform.current.isAndroid())) {
                    ScreenshotButton(
                        onClick = onClickScreenshot,
                    )
                }
            },
            gestureLock = {
                if (expanded) {
                    GestureLock(isLocked = isLocked, onClick = { isLocked = !isLocked })
                }
            },
            bottomBar = {
                PlayerControllerBar(
                    startActions = {
                        val playWhenReady by remember(playerState) { playerState.state.map { it.playWhenReady } }
                            .collectAsStateWithLifecycle(false)
                        PlayerControllerDefaults.PlaybackIcon(
                            isPlaying = { playWhenReady },
                            onClick = { playerState.togglePlayWhenReady() },
                        )

                        if (hasNextEpisode && expanded) {
                            PlayerControllerDefaults.NextEpisodeIcon(
                                onClick = onClickNextEpisode,
                            )
                        }
                        val audioLevelController = audioController as? MediampAudioLevelController

                        val hasMouse = hasPointerDevice(
                            LocalPlatform.current,
                            LocalActiveInputSource.current.hasSeenMouse,
                        )
                        if (expanded && audioLevelController != null && hasMouse) {
                            val level by audioLevelController.levelFlow.collectAsState()
                            val isMute by audioLevelController.muteFlow.collectAsState()

                            PlayerControllerDefaults.AudioIcon(
                                level,
                                isMute = isMute,
                                maxValue = audioLevelController.range.endInclusive,
                                onClick = {
                                    audioLevelController.toggleMute()
                                },
                                onchange = {
                                    audioLevelController.setLevel(it)
                                },
                                controllerState = playerControllerState,
                            )
                        }
                    },
                    progressIndicator = {
                        MediaProgressIndicatorText(
                            progressSliderState,
                            playbackSpeedState = playbackSpeedControllerState,
                        )
                    },
                    progressSlider = {
                        PlayerControllerDefaults.MediaProgressSlider(
                            progressSliderState,
                            cacheProgressInfoFlow = cacheProgressInfoFlow,
                            showPreviewTimeTextOnThumb = expanded,
                            framePreview = framePreview,
                            showFramePreviewInPopup = expanded,
                            touchSeekState = touchSeekState,
                        )
                    },
                    endActions = {
                        if (expanded) {
                            PlayerControllerDefaults.SelectEpisodeIcon(
                                onClick = { sheetsController.navigateTo(EpisodeVideoSideSheetPage.EPISODE_SELECTOR) },
                            )

                            if (LocalPlatform.current.isDesktop()) {
                                playerState.audioTracks?.let {
                                    PlayerControllerDefaults.AudioSwitcher(it)
                                }
                            }

                            playerState.subtitleTracks?.let {
                                PlayerControllerDefaults.SubtitleSwitcher(it)
                            }

                            val videoAspectRatioAlwaysOnRequester =
                                rememberAlwaysOnRequester(playerControllerState, "videoAspectRatioSelector")
                            videoAspectRatioControllerState?.also { controller ->
                                VideoAspectRatioSelector(controller) {
                                    if (it) {
                                        videoAspectRatioAlwaysOnRequester.request()
                                    } else {
                                        videoAspectRatioAlwaysOnRequester.cancelRequest()
                                    }
                                }
                            }

                            val playbackSpeedAlwaysOnRequester =
                                rememberAlwaysOnRequester(playerControllerState, "speedSwitcher")
                            playbackSpeedControllerState?.also { controller ->
                                SpeedSwitcher(controller) {
                                    if (it) {
                                        playbackSpeedAlwaysOnRequester.request()
                                    } else {
                                        playbackSpeedAlwaysOnRequester.cancelRequest()
                                    }
                                }
                            }
                        }
                        PlayerControllerDefaults.FullscreenIcon(fullscreenState)
                    },
                    expanded = expanded,
                    sliderOnly = playerControllerState.visibility == ControllerVisibility.InlineSliderOnly,
                )
            },
            detachedProgressSlider = detachedProgressSlider,
            floatingBottomEnd = { fullscreenSwitchButton() },
            rhsSheet = { sideSheets(sheetsController) },
            leftBottomTips = leftBottomTips,
        )
    }
}

@Composable
private fun rememberPlayerTouchSeekState(
    controllerState: PlayerControllerState,
    indicatorState: GestureIndicatorState,
    swipeSeekerConfig: SwipeSeekerConfig,
): TouchSeekState {
    val density = LocalDensity.current
    return remember(controllerState, indicatorState, swipeSeekerConfig, density) {

        val controllerRequester = Any()
        var indicatorTicket: Int? = null
        fun stopCancellationIndicator() {
            indicatorTicket?.let(indicatorState::stopSeekCancellation)
            indicatorTicket = null
        }
        TouchSeekState(
            swipeSeekerConfig = swipeSeekerConfig,
            density = density,
            onStateChanged = { state ->
                when (state) {

                    TouchSeekState.State.Idle -> {
                        controllerState.cancelRequestInlineProgressSlider(controllerRequester)
                        stopCancellationIndicator()
                    }

                    TouchSeekState.State.Seeking -> {
                        controllerState.setRequestInlineProgressSlider(controllerRequester)
                        stopCancellationIndicator()
                    }

                    TouchSeekState.State.Cancelling -> {
                        indicatorTicket = indicatorState.startSeekCancellation()
                    }
                }
            },
        )
    }
}

@Composable
private fun EpisodeVideoTopBarActions(
    playerState: MediampPlayer,
    expanded: Boolean,
    opEdSkipDuration: Duration,
    onClickSkipOpEd: (currentPositionMillis: Long) -> Unit,
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage>,
    shareData: MediaShareData,
    onClickCache: () -> Unit,
    playerControllerState: PlayerControllerState,
    videoEnhancement: VideoEnhancementController?,
    sidebarVisible: Boolean,
    onToggleSidebar: (isCollapsed: Boolean) -> Unit,
    playerStatsVisible: Boolean,
    onTogglePlayerStats: () -> Unit,
    alwaysOnTop: Boolean = false,
    onToggleAlwaysOnTop: (() -> Unit)? = null,
) {
    var showShareDropdown by rememberSaveable { mutableStateOf(false) }
    var showMoreDropdown by rememberSaveable { mutableStateOf(false) }
    var showVideoEnhancementDropdown by rememberSaveable { mutableStateOf(false) }

    val dropdownAlwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "topBarExternalActions")
    val isExternalDropdownVisible = showShareDropdown || showMoreDropdown || showVideoEnhancementDropdown
    val skipDurationSeconds = opEdSkipDuration.inWholeSeconds

    val fastForwardSecondsText = stringResource(Lang.subject_episode_fast_forward_seconds, skipDurationSeconds)
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    val moreOptionsText = stringResource(Lang.subject_episode_more_options)
    val externalLinksText = stringResource(Lang.subject_episode_external_links)
    val cacheText = stringResource(Lang.subject_episode_cache)
    val showPlayerStatsText = stringResource(Lang.video_player_stats_title_show)
    val hidePlayerStatsText = stringResource(Lang.video_player_stats_title_hide)
    val collapseSidebarText = stringResource(Lang.subject_episode_collapse_sidebar)
    val expandSidebarText = stringResource(Lang.subject_episode_expand_sidebar)
    val videoEnhancementTitleText = stringResource(Lang.video_player_video_enhancement)

    DisposableEffect(dropdownAlwaysOnRequester, isExternalDropdownVisible) {
        if (isExternalDropdownVisible) {
            dropdownAlwaysOnRequester.request()
        } else {
            dropdownAlwaysOnRequester.cancelRequest()
        }
        onDispose {
            if (isExternalDropdownVisible) {
                dropdownAlwaysOnRequester.cancelRequest()
            }
        }
    }

    IconButton({ onClickSkipOpEd(playerState.currentPositionMillis.value) }) {
        val icon = when (skipDurationSeconds) {
            85L -> WynimeIcons.Forward85
            90L -> WynimeIcons.Forward90
            else -> WynimeIcons.Forward80
        }
        Icon(icon, fastForwardSecondsText)
    }

    if (expanded) {
        if (videoEnhancement != null) {
            Box {
                val mode by videoEnhancement.mode.collectAsState()
                IconButton(
                    onClick = { showVideoEnhancementDropdown = true },
                    modifier = Modifier.testTag(TAG_VIDEO_ENHANCEMENT),
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = videoEnhancementTitleText,
                        tint = if (mode != VideoEnhancementMode.OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }

                VideoEnhancementDropdown(
                    videoEnhancement,
                    showVideoEnhancementDropdown,
                    onDismissRequest = { showVideoEnhancementDropdown = false },
                )
            }
        }

        IconButton(
            { sheetsController.navigateTo(EpisodeVideoSideSheetPage.MEDIA_SELECTOR) },
            Modifier.testTag(TAG_SHOW_MEDIA_SELECTOR),
        ) {
            Icon(Icons.Rounded.DisplaySettings, contentDescription = selectMediaSourceText)
        }
    }

    if (LocalPlatform.current.isDesktop() && onToggleAlwaysOnTop != null) {
        val alwaysOnTopText = stringResource(Lang.always_on_top)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(alwaysOnTopText) } },
            state = rememberTooltipState(),
        ) {
            IconButton(onToggleAlwaysOnTop) {
                Icon(
                    if (alwaysOnTop) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = alwaysOnTopText,
                )
            }
        }
    }

    Box {
        IconButton({ showMoreDropdown = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = moreOptionsText)
        }
        DropdownMenu(
            expanded = showMoreDropdown,
            onDismissRequest = { showMoreDropdown = false },
        ) {
            if (!expanded && videoEnhancement != null) {
                val mode by videoEnhancement.mode.collectAsState()
                DropdownMenuItem(
                    text = { Text(videoEnhancementTitleText) },
                    onClick = {
                        showMoreDropdown = false
                        showVideoEnhancementDropdown = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = videoEnhancementTitleText,
                            tint = if (mode != VideoEnhancementMode.OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (playerStatsVisible) hidePlayerStatsText else showPlayerStatsText) },
                onClick = {
                    showMoreDropdown = false
                    onTogglePlayerStats()
                },
                leadingIcon = { Icon(Icons.Outlined.Analytics, null) },
            )
            DropdownMenuItem(
                text = { Text(externalLinksText) },
                onClick = {
                    showMoreDropdown = false
                    showShareDropdown = true
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
            )
            DropdownMenuItem(
                text = { Text(cacheText) },
                onClick = {
                    showMoreDropdown = false
                    onClickCache()
                },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
            )
        }
        ShareEpisodeDropdown(
            shareData,
            showShareDropdown,
            onDismissRequest = { showShareDropdown = false },
        )
        if (videoEnhancement != null && !expanded) {
            VideoEnhancementDropdown(
                videoEnhancement,
                showVideoEnhancementDropdown,
                onDismissRequest = { showVideoEnhancementDropdown = false },
            )
        }
    }

    if (expanded && LocalPlatform.current.isDesktop()) {
        IconButton(
            { onToggleSidebar(!sidebarVisible) },
            Modifier.testTag(TAG_COLLAPSE_SIDEBAR),
        ) {
            if (sidebarVisible) {
                Icon(WynimeIcons.RightPanelClose, contentDescription = collapseSidebarText)
            } else {
                Icon(WynimeIcons.RightPanelOpen, contentDescription = expandSidebarText)
            }
        }
    }
}

@Stable
object EpisodeVideoDefaults

