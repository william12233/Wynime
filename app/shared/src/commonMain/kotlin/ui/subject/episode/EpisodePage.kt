package com.wynime.app.ui.subject.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.comment.CommentContext
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.features.StreamType
import com.wynime.app.platform.features.getComponentAccessors
import com.wynime.app.tools.rememberUiMonoTasker
import com.wynime.app.ui.comment.CommentEditorState
import com.wynime.app.ui.comment.CommentReportHost
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.ImageViewer
import com.wynime.app.ui.foundation.ImageViewerBackHandler
import com.wynime.app.ui.foundation.LocalImageViewerHandler
import com.wynime.app.ui.foundation.LocalIsPreviewing
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.WindowDropHandlerEffect
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.effects.DarkStatusBarAppearance
import com.wynime.app.ui.foundation.effects.OnLifecycleEvent
import com.wynime.app.ui.foundation.effects.OverrideCaptionButtonAppearance
import com.wynime.app.ui.foundation.effects.ScreenOnEffect
import com.wynime.app.ui.foundation.effects.ScreenRotationEffect
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.touchHorizontalScrollOnly
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isHeightCompact
import com.wynime.app.ui.foundation.layout.isWidthAtLeastExpanded
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthCompact
import com.wynime.app.ui.foundation.layout.setRequestFullScreen
import com.wynime.app.ui.foundation.layout.setSystemBarVisible
import com.wynime.app.ui.foundation.navigation.BackHandler
import com.wynime.app.ui.foundation.pagerTabIndicatorOffset
import com.wynime.app.ui.foundation.rememberImageViewerHandler
import com.wynime.app.ui.foundation.theme.WynimeTheme
import com.wynime.app.ui.foundation.theme.LocalThemeSettings
import com.wynime.app.ui.foundation.theme.isSystemInDarkThemeDetected
import com.wynime.app.ui.foundation.theme.weaken
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.episode_comments
import com.wynime.app.ui.lang.episode_comments_with_count
import com.wynime.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import com.wynime.app.ui.lang.foundation_richtext_open_failed_prefix
import com.wynime.app.ui.lang.subject_details_tab_details
import com.wynime.app.ui.richtext.RichTextDefaults
import com.wynime.app.ui.subject.episode.comments.EpisodeCommentColumn
import com.wynime.app.ui.subject.episode.comments.EpisodeEditCommentSheet
import com.wynime.app.ui.subject.episode.details.EpisodeDetails
import com.wynime.app.ui.subject.episode.notif.VideoNotifEffect
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import com.wynime.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import com.wynime.app.ui.subject.episode.video.components.SideSheets
import com.wynime.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import com.wynime.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import com.wynime.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.PlayerFullscreenState
import com.wynime.app.videoplayer.ui.VideoAspectRatioControllerState
import com.wynime.app.videoplayer.ui.gesture.LevelController
import com.wynime.app.videoplayer.ui.gesture.NoOpLevelController
import com.wynime.app.videoplayer.ui.gesture.asLevelController
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults
import com.wynime.app.videoplayer.ui.progress.rememberMediaProgressFramePreviewState
import com.wynime.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import com.wynime.app.videoplayer.ui.rememberPlayerFullscreenState
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.utils.platform.isAndroid
import com.wynime.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.Screenshots
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.features.toggleMute

@Composable
fun EpisodeScreen(
    viewModel: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val themeSettings = LocalThemeSettings.current
    WynimeTheme(
        darkModeOverride = if (themeSettings.alwaysDarkInEpisodePage) DarkMode.DARK else null,
    ) {
        Column(modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
            ) {
                EpisodeScreenContent(
                    viewModel,
                    Modifier,
                    windowInsets = windowInsets,
                )
            }
        }
    }
}

@Composable
private fun EpisodeScreenContent(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {

    val context by rememberUpdatedState(LocalContext.current)
    val window = LocalPlatformWindow.current
    val scope = rememberCoroutineScope()

    DisposableEffect(window, vm) {
        onDispose {
            if (vm.desktopAlwaysOnTopSetByPlayer) {
                vm.desktopAlwaysOnTopSetByPlayer = false
                window.setAlwaysOnTop(false)
            }
        }
    }

    val fullscreenState = rememberEpisodeFullscreenState(vm)
    BackHandler(enabled = fullscreenState.isFullscreen) { fullscreenState.request(false) }

    val imageViewer = rememberImageViewerHandler()
    ImageViewerBackHandler(imageViewer)

    val playerState by vm.player.state.collectAsStateWithLifecycle()
    if (playerState.playWhenReady) {
        ScreenOnEffect()
    }

    var showEditCommentSheet by rememberSaveable { mutableStateOf(false) }
    var didSetPaused by rememberSaveable { mutableStateOf(false) }

    val pauseOnPlaying: () -> Unit = {
        if (vm.player.state.value.playWhenReady) {
            didSetPaused = true
            vm.player.pause()
        } else {
            didSetPaused = false
        }
    }
    val tryUnpause: () -> Unit = {
        if (didSetPaused) {
            didSetPaused = false
            vm.player.play()
        }
    }

    AutoPauseEffect(vm, enabled = true)
    DisplayModeEffect(vm.videoScaffoldConfig)

    VideoNotifEffect(vm)

    WindowDropHandlerEffect(rememberEpisodeVideoDropHandler { vm.playDroppedFile(it) })

    DarkStatusBarAppearance()

    if (vm.videoScaffoldConfig.autoFullscreenOnLandscapeMode) {
        ScreenRotationEffect {
            vm.isFullscreen = it
        }
    }

    if (LocalPlatform.current.isDesktop()) {
        LaunchedEffect(window, vm) {
            snapshotFlow { window.isUndecoratedFullscreen }.collect { vm.isFullscreen = it }
        }
    }

    LaunchedEffect(vm.isFullscreen) {

        context.setSystemBarVisible(window, !vm.isFullscreen)
    }

    LaunchedEffect(Unit) {
        val audioController = vm.player.features[AudioLevelController]
        if (audioController != null) {
            val persistedPlayerVolume = vm.playerVolumeFlow.first()
            audioController.setVolume(persistedPlayerVolume.level)
            audioController.setMute(persistedPlayerVolume.mute)
        }
    }

    BoxWithConstraints(modifier) {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass

        val showExpandedUI = when {
            windowSizeClass.isWidthCompact && windowSizeClass.isHeightCompact -> false
            windowSizeClass.isWidthCompact && windowSizeClass.isHeightAtLeastMedium -> false
            windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightCompact -> true
            windowSizeClass.isWidthAtLeastExpanded -> true
            else -> false
        }

        if (vm.isFullscreen || !showExpandedUI) {
            OverrideCaptionButtonAppearance(isDark = true)
        }

        val pageState = vm.pageState.collectAsStateWithLifecycle()

        when (val page = pageState.value) {
            null -> {

            }

            else -> {
                CompositionLocalProvider(LocalImageViewerHandler provides imageViewer) {
                    when {
                        showExpandedUI ->
                            EpisodeScreenTabletVeryWide(
                                vm,
                                page,
                                page.fetchRequest,
                                { vm.updateFetchRequest(it) },
                                pauseOnPlaying = pauseOnPlaying,
                                setShowEditCommentSheet = { showEditCommentSheet = it },
                                modifier = Modifier.fillMaxSize(),
                                windowInsets = windowInsets,
                            )

                        else -> EpisodeScreenContentPhone(
                            vm,
                            page,
                            Modifier.fillMaxSize(),
                            pauseOnPlaying = pauseOnPlaying,
                            setShowEditCommentSheet = { showEditCommentSheet = it },
                            windowInsets,
                        )
                    }
                }
            }
        }
        ImageViewer(imageViewer) { imageViewer.clear() }
    }

    CommentReportHost(vm.commentReportState)

    if (showEditCommentSheet) {
        EpisodeEditCommentSheet(
            state = vm.commentEditorState,
            onDismiss = {
                showEditCommentSheet = false
                vm.commentEditorState.cancelSend()
                tryUnpause()
            },
            onSendComplete = {
                scope.launch {
                    vm.commentLazyGirdState.scrollToItem(0)
                }
            },
        )
    }

    vm.mediaResolver.ComposeContent()
}

@Composable
private fun EpisodeScreenTabletVeryWide(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    pauseOnPlaying: () -> Unit,
    setShowEditCommentSheet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    BoxWithConstraints {
        val maxWidth = maxWidth
        Row(
            modifier
                .then(
                    if (vm.isFullscreen) Modifier.fillMaxSize()
                    else Modifier,
                ),
        ) {
            EpisodeVideo(

                vm,
                page,
                vm.playerControllerState,
                expanded = true,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                maintainAspectRatio = false,
                windowInsets = if (vm.isFullscreen) {
                    fullscreenVideoWindowInsets(windowInsets)
                } else {

                    windowInsets.only(WindowInsetsSides.Left + WindowInsetsSides.Vertical)
                },
            )

            if (vm.isFullscreen || !vm.sidebarVisible) {
                return@Row
            }

            val pagerState = rememberPagerState(initialPage = 0) { 2 }
            val scope = rememberCoroutineScope()

            Column(
                Modifier
                    .width(
                        width = (maxWidth * 0.25f)
                            .coerceIn(340.dp, 460.dp),
                    )
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Right))
                    .background(MaterialTheme.colorScheme.background),
            ) {

                val themeSettings = LocalThemeSettings.current
                val isEpPageDarkTheme = when {
                    themeSettings.alwaysDarkInEpisodePage -> true
                    themeSettings.darkMode == DarkMode.AUTO -> isSystemInDarkThemeDetected()
                    else -> themeSettings.darkMode == DarkMode.DARK
                }

                val needShadeBackground = !isEpPageDarkTheme && LocalPlatform.current.isAndroid()

                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .ifThen(needShadeBackground) {
                            background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.scrim,
                                        Color.Transparent,
                                    ),
                                ),
                            )
                        }
                        .windowInsetsPadding(

                            WindowInsets.safeContent
                                .only(WindowInsetsSides.Top),
                        ),
                )

                TabRow(
                    pagerState, scope, { vm.episodeCommentState.count }, Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.weaken())

                HorizontalPager(
                    state = pagerState,
                    Modifier.fillMaxSize().touchHorizontalScrollOnly(),
                ) { index ->
                    when (index) {
                        0 -> Box(Modifier.fillMaxSize()) {
                            val navigator = LocalNavigator.current
                            val pageState by vm.pageState.collectAsStateWithLifecycle()
                            val toaster = LocalToaster.current
                            pageState?.let { page ->
                                EpisodeDetails(
                                    page.mediaSelectorSummary,
                                    vm.episodeDetailsState,
                                    fetchRequest,
                                    onFetchRequestChange,
                                    vm.episodeCarouselState,
                                    vm.editableSubjectCollectionTypeState,
                                    page.mediaSelectorState,
                                    page.selfInfo,
                                    modifier = Modifier.fillMaxSize(),
                                    onSwitchEpisode = { episodeId ->
                                        if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                                            navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                                        }
                                    },
                                    onRestartSource = { vm.restartSource(it) },
                                    onClickLogin = { navigator.navigateBangumiAuthorize() },
                                    onClickTag = { navigator.navigateSubjectSearch(it.name) },
                                    onEpisodeCollectionUpdate = { request ->
                                        scope.launch {
                                            vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                                                toaster.showLoadError(it)
                                            }
                                        }
                                    },
                                    loadError = page.loadError,
                                    onRetryLoad = {
                                        page.loadError?.let { vm.retryLoad(it) }
                                    },
                                )
                            }
                        }

                        1 -> {
                            EpisodeCommentColumn(
                                commentState = vm.episodeCommentState,
                                commentReportState = vm.commentReportState,
                                commentEditorState = vm.commentEditorState,
                                subjectId = vm.subjectId,
                                episodeId = page.episodePresentation.episodeId,
                                setShowEditCommentSheet = setShowEditCommentSheet,
                                pauseOnPlaying = pauseOnPlaying,
                                gridState = vm.commentLazyGirdState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRow(
    pagerState: PagerState,
    scope: CoroutineScope,
    commentCount: () -> Int?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val detailsText = stringResource(Lang.subject_details_tab_details)
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier,
        indicator = @Composable { tabPositions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
            )
        },
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor),
        edgePadding = 0.dp,
        divider = {},
    ) {
        Tab(
            selected = pagerState.currentPage == 0,
            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            modifier = Modifier.height(44.dp),
            text = { Text(detailsText, softWrap = false) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
        )
        Tab(
            selected = pagerState.currentPage == 1,
            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
            modifier = Modifier.height(44.dp),
            text = {
                val count = commentCount()
                val text = if (count == null) {
                    stringResource(Lang.episode_comments)
                } else {
                    stringResource(Lang.episode_comments_with_count, count)
                }
                Text(text, softWrap = false)
            },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EpisodeScreenContentPhone(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    modifier: Modifier = Modifier,
    pauseOnPlaying: () -> Unit,
    setShowEditCommentSheet: (Boolean) -> Unit,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val toaster = LocalToaster.current
    val defaultVideoWindowInsets = windowInsets
        .union(WindowInsets.desktopTitleBar)
        .run {

            only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        }
    val videoWindowInsets = if (vm.isFullscreen) {
        fullscreenVideoWindowInsets(defaultVideoWindowInsets)
    } else {
        defaultVideoWindowInsets
    }
    val columnInsets = defaultVideoWindowInsets.only(WindowInsetsSides.Horizontal)

    EpisodeScreenContentPhoneScaffold(
        videoOnly = vm.isFullscreen,
        commentCount = { vm.episodeCommentState.count },
        video = {
            EpisodeVideo(
                vm, page,
                vm.playerControllerState, vm.isFullscreen,
                windowInsets = videoWindowInsets,
            )
        },
        headlineContent = {

        },
        episodeDetails = {
            val navigator = LocalNavigator.current
            val pageState by vm.pageState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            pageState?.let { page ->
                EpisodeDetails(
                    page.mediaSelectorSummary,
                    vm.episodeDetailsState,
                    page.fetchRequest,
                    { vm.updateFetchRequest(it) },
                    vm.episodeCarouselState,
                    vm.editableSubjectCollectionTypeState,
                    page.mediaSelectorState,
                    page.selfInfo,
                    onSwitchEpisode = { episodeId ->
                        if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                            navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                        }
                    },
                    onRestartSource = { vm.restartSource(it) },
                    onClickLogin = { navigator.navigateBangumiAuthorize() },
                    onClickTag = { navigator.navigateSubjectSearch(it.name) },
                    onEpisodeCollectionUpdate = { request ->
                        scope.launch {
                            vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                                toaster.showLoadError(it)
                            }
                        }
                    },
                    loadError = page.loadError,
                    onRetryLoad = {
                        page.loadError?.let { vm.retryLoad(it) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        commentColumn = {
            EpisodeCommentColumn(
                commentState = vm.episodeCommentState,
                commentReportState = vm.commentReportState,
                commentEditorState = vm.commentEditorState,
                subjectId = vm.subjectId,
                episodeId = page.episodePresentation.episodeId,
                setShowEditCommentSheet = setShowEditCommentSheet,
                pauseOnPlaying = pauseOnPlaying,
                gridState = vm.commentLazyGirdState,
            )
        },
        modifier = modifier.then(
            if (vm.isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.windowInsetsPadding(columnInsets)
            },
        ),
    )

}

@Composable
fun EpisodeScreenContentPhoneScaffold(
    videoOnly: Boolean,
    commentCount: () -> Int?,
    video: @Composable () -> Unit,
    episodeDetails: @Composable () -> Unit,
    commentColumn: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit = {},
    tabRowContent: @Composable () -> Unit = {},
) {
    Column(modifier) {
        video()

        if (videoOnly) {
            return@Column
        }

        val pagerState = rememberPagerState(initialPage = 0) { 2 }
        val scope = rememberCoroutineScope()

        Column(Modifier.fillMaxSize()) {
            headlineContent()
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row {
                    TabRow(
                        pagerState, scope, commentCount, Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    Box(
                        modifier = Modifier.weight(0.618f)
                            .height(44.dp)
                            .padding(vertical = 4.dp, horizontal = 16.dp),
                    ) {
                        Row(Modifier.align(Alignment.CenterEnd)) {
                            tabRowContent()
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.weaken())

            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { index ->
                Box(Modifier.fillMaxSize()) {
                    when (index) {
                        0 -> {
                            episodeDetails()
                        }

                        1 -> {
                            commentColumn()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun fullscreenVideoWindowInsets(default: WindowInsets): WindowInsets {
    return default
}

@Composable
private fun rememberEpisodeFullscreenState(vm: EpisodeViewModel): PlayerFullscreenState {
    val context by rememberUpdatedState(LocalContext.current)
    val window = LocalPlatformWindow.current
    val scope = rememberCoroutineScope()
    return rememberPlayerFullscreenState(
        isFullscreen = { vm.isFullscreen },
        onRequest = { fullscreen ->
            scope.launch {

                if (fullscreen) {
                    vm.isFullscreen = true
                    context.setRequestFullScreen(window, true)
                } else {
                    context.setRequestFullScreen(window, false)
                    vm.isFullscreen = false
                }
            }
        },
    )
}

@Composable
private fun EpisodeVideo(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    playerControllerState: PlayerControllerState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val context by rememberUpdatedState(LocalContext.current)
    val navigator = LocalNavigator.current
    val isAndroid = LocalPlatform.current.isAndroid()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        playerControllerState.toggleFullVisible(false)
    }

    val window = LocalPlatformWindow.current

    SideEffect {
        vm.onUIReady()
    }

    val progressSliderState = rememberMediaProgressSliderState(
        vm.player,
        vm.progressChaptersFlow,
        onPreview = {

        },
        onPreviewFinished = {
            vm.player.seekTo(it)
        },
    )
    val framePreview = if (vm.videoScaffoldConfig.enableFramePreview) {
        rememberMediaProgressFramePreviewState(vm.player)
    } else {
        null
    }
    val scope = rememberCoroutineScope()

    val platformComponents by remember {
        derivedStateOf {
            context.getComponentAccessors()
        }
    }
    val fullscreenState = rememberEpisodeFullscreenState(vm)

    EpisodeVideoImpl(
        vm.player,
        expanded = expanded,
        hasNextEpisode = vm.episodeSelectorState.hasNextEpisode,
        onClickNextEpisode = { vm.episodeSelectorState.selectNext() },
        playerControllerState = playerControllerState,
        opEdSkipDuration = vm.videoScaffoldConfig.opEdSkipDuration,
        onClickSkipOpEd = { vm.onClickSkipOpEd(it) },
        title = {
            val episode = page.episodePresentation
            val subject = page.subjectPresentation
            val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
            EpisodePlayerTitle(
                episode.ep,
                if (useOriginalTitle) episode.originalTitle else episode.title,
                if (useOriginalTitle) subject.originalTitle else subject.title,
                Modifier.placeholder(episode.isPlaceholder || subject.isPlaceholder),
            )
        },
        videoLoadingStateFlow = vm.videoStatisticsFlow.map { it.videoLoadingState },
        fullscreenState = fullscreenState,
        alwaysOnTop = window.isAlwaysOnTop,
        onToggleAlwaysOnTop = {
            val newValue = !window.isAlwaysOnTop
            window.setAlwaysOnTop(newValue)
            vm.desktopAlwaysOnTopSetByPlayer = newValue
        },
        onClickScreenshot = {
            val currentPositionMillis = vm.player.currentPositionMillis.value
            val min = currentPositionMillis / 60000
            val sec = (currentPositionMillis - (min * 60000)) / 1000
            val ms = currentPositionMillis - (min * 60000) - (sec * 1000)
            val currentPosition = "${min}m${sec}s${ms}ms"

            val filename = "${vm.subjectId}-${page.episodePresentation.ep}-${currentPosition}.png"
            scope.launch {
                if (isAndroid) {
                    takeAndroidPlayerScreenshot(context, vm.player, filename)
                } else {
                    vm.player.features[Screenshots]?.takeScreenshot(filename)
                }
            }
        },
        detachedProgressSlider = {
            PlayerControllerDefaults.MediaProgressSlider(
                progressSliderState,
                cacheProgressInfoFlow = vm.cacheProgressInfoFlow,
                enabled = false,
                framePreview = framePreview,
                showFramePreviewInPopup = expanded,
            )
        },
        sidebarVisible = vm.sidebarVisible,
        onToggleSidebar = {
            vm.sidebarVisible = it
        },
        progressSliderState = progressSliderState,
        cacheProgressInfoFlow = vm.cacheProgressInfoFlow,
        framePreview = framePreview,
        audioController = remember {
            derivedStateOf {
                platformComponents.audioManager?.asLevelController(StreamType.MUSIC)
                    ?: vm.player.features[AudioLevelController]
                        ?.let { MediampAudioLevelController(it, vm::savePlayerVolume) }
                    ?: NoOpLevelController
            }
        }.value,
        brightnessController = remember {
            derivedStateOf {
                platformComponents.brightnessManager?.asLevelController() ?: NoOpLevelController
            }
        }.value,
        playbackSpeedControllerState = run {
            val playbackSpeed = vm.player.features[PlaybackSpeed]
            remember(playbackSpeed) {
                playbackSpeed?.let {
                    PlaybackSpeedControllerState(
                        playbackSpeed = it,
                        rangeProvider = { vm.playbackSpeedRange },
                        onCommitSpeed = { speed -> vm.setPlaybackSpeed(speed) },
                        scope = scope,
                    )
                }
            }
        },
        videoAspectRatioControllerState = remember {
            vm.player.features[VideoAspectRatio]?.let { VideoAspectRatioControllerState(it, scope = scope) }
        },
        videoEnhancement = vm.videoEnhancement,
        leftBottomTips = {
            WynimeAnimatedVisibility(
                visible = vm.playerSkipOpEdState.showSkipTips,
            ) {
                PlayerControllerDefaults.LeftBottomTips(
                    onClick = {
                        vm.playerSkipOpEdState.cancelSkipOpEd()
                    },
                )
            }
        },
        fullscreenSwitchButton = {
            EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                vm.videoScaffoldConfig.fullscreenSwitchMode,
                fullscreenState,
            )
        },
        sideSheets = { sheetsController ->
            EpisodeVideoDefaults.SideSheets(
                sheetsController,
                playerControllerState,
                mediaSelectorPage = {
                    val pageState by vm.pageState.collectAsStateWithLifecycle()
                    pageState?.let { page ->
                        EpisodeVideoSideSheets.MediaSelectorSheet(
                            mediaSelectorState = page.mediaSelectorState,
                            fetchRequest = page.fetchRequest,
                            onFetchRequestChange = { vm.updateFetchRequest(it) },
                            onDismissRequest = { goBack() },
                            onRestartSource = { vm.restartSource(it) },
                        )
                    }
                },
                episodeSelectorPage = {
                    EpisodeVideoSideSheets.EpisodeSelectorSheet(
                        vm.episodeSelectorState,
                        onDismissRequest = { goBack() },
                    )
                },
            )
        },
        shareData = page.shareData,
        onClickCache = { navigator.navigateSubjectCaches(vm.subjectId) },
        modifier = modifier
            .fillMaxWidth().background(Color.Black)
            .then(if (expanded) Modifier.fillMaxSize() else Modifier.statusBarsPadding()),
        maintainAspectRatio = maintainAspectRatio,
        contentWindowInsets = windowInsets,
        fastForwardSpeed = vm.videoScaffoldConfig.fastForwardSpeed,
    )
}

@Composable
private fun EpisodeCommentColumn(
    commentState: CommentState,
    commentReportState: CommentReportState,
    commentEditorState: CommentEditorState,
    subjectId: Int,
    episodeId: Int,
    setShowEditCommentSheet: (Boolean) -> Unit,
    pauseOnPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current
    val externalAppLinkWarningPrefix = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val openLinkFailedPrefix = stringResource(Lang.foundation_richtext_open_failed_prefix)

    EpisodeCommentColumn(
        state = commentState,
        reportState = commentReportState,
        episodeId = episodeId,
        onClickReply = {
            setShowEditCommentSheet(true)
            commentEditorState.startEdit(CommentContext.EpisodeReply(subjectId, episodeId.toLong(), it))
            pauseOnPlaying()

        },
        onNewCommentClick = {
            commentEditorState.startEdit(
                CommentContext.Episode(subjectId, episodeId.toLong()),
            )
            setShowEditCommentSheet(true)
        },
        onClickUrl = {
            RichTextDefaults.checkSanityAndOpen(
                it,
                browserNavigator,
                toaster,
                externalAppLinkWarningPrefix,
                openLinkFailedPrefix,
            )
        },
        modifier = modifier.fillMaxSize(),
        gridState = gridState,
    )
}

@Composable
private fun AutoPauseEffect(viewModel: EpisodeViewModel, enabled: Boolean) {
    var pausedVideo by rememberSaveable { mutableStateOf(true) }
    if (LocalIsPreviewing.current || !enabled) return

    val autoPauseTasker = rememberUiMonoTasker()
    OnLifecycleEvent {
        if (it == Lifecycle.Event.ON_STOP) {
            if (viewModel.player.state.value.playWhenReady) {
                pausedVideo = true
                autoPauseTasker.launch {

                    viewModel.player.pause()
                }
            } else {

                pausedVideo = false
            }
        } else if (it == Lifecycle.Event.ON_START && pausedVideo) {
            autoPauseTasker.launch {
                viewModel.player.play()
            }
            pausedVideo = false
        }
    }
}

@Composable
internal expect fun DisplayModeEffect(config: VideoScaffoldConfig)

class MediampAudioLevelController(
    private val controller: AudioLevelController,
    private val onVolumeStateChanged: (level: Float, mute: Boolean) -> Unit,
) : LevelController {
    override val level: Float get() = controller.volume.value

    val levelFlow = controller.volume
    val muteFlow = controller.isMute

    override val range: ClosedRange<Float> = 0f..controller.maxVolume

    override fun setLevel(level: Float) {
        val newLevel = level.coerceIn(range)
        controller.setVolume(newLevel)
        onVolumeStateChanged(newLevel, controller.isMute.value)
    }

    fun toggleMute() {
        val targetIsMute = !muteFlow.value
        controller.toggleMute()
        onVolumeStateChanged(level, targetIsMute)
    }
}

@Composable
@Preview(widthDp = 1080 / 3, heightDp = 2400 / 3, showBackground = true)
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
internal fun PreviewEpisodePage() {
    ProvideCompositionLocalsForPreview {
        val context = LocalContext.current
        EpisodeScreen(
            remember {
                EpisodeViewModel(
                    424663,
                    1277147,
                    context = context,
                )
            },
        )
    }
}

@Composable
@PreviewLightDark
fun PreviewEpisodeSceneContentPhoneScaffoldTabs() {
    ProvideCompositionLocalsForPreview {
        EpisodeScreenContentPhoneScaffold(
            videoOnly = false,
            commentCount = { 100 },
            video = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            },
            episodeDetails = { },
            commentColumn = { },
        )
    }
}
