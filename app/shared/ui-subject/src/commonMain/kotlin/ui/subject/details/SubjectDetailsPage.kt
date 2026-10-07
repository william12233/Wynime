package com.wynime.app.ui.subject.details

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmpalette.rememberPaletteState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.data.models.subject.TestSubjectInfo
import com.wynime.app.data.models.subject.preferredDisplayName
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeRequest
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.ui.comment.CommentReportHost
import com.wynime.app.ui.comment.UIComment
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.WynimeImageLoadSuccess
import com.wynime.app.ui.foundation.ImageViewer
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.Tag
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.touchHorizontalScrollOnly
import com.wynime.app.ui.foundation.interaction.WindowDragArea
import com.wynime.app.ui.foundation.layout.NestedScrollableColumn
import com.wynime.app.ui.foundation.layout.NestedScrollableColumnState
import com.wynime.app.ui.foundation.layout.NestedScrollableScope
import com.wynime.app.ui.foundation.layout.PaddingValuesSides
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthCompact
import com.wynime.app.ui.foundation.layout.only
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.layout.paneVerticalPadding
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.foundation.layout.rememberNestedScrollableColumnState
import com.wynime.app.ui.foundation.ImageViewerBackHandler
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.pagerTabIndicatorOffset
import com.wynime.app.ui.foundation.rememberImageViewerHandler
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.LocalAppChromeHazeState
import com.wynime.app.ui.foundation.theme.LocalThemeSettings
import com.wynime.app.ui.foundation.theme.MaterialThemeFromPaletteAndImage
import com.wynime.app.ui.foundation.theme.appChromeFrostedGlass
import com.wynime.app.ui.foundation.theme.appChromeHazeSource
import com.wynime.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import com.wynime.app.ui.lang.foundation_richtext_open_failed_prefix
import com.wynime.app.ui.lang.subject_details_coming_soon
import com.wynime.app.ui.lang.subject_details_login_to_collect
import com.wynime.app.ui.lang.subject_details_tab_comments
import com.wynime.app.ui.lang.subject_details_tab_details
import com.wynime.app.ui.lang.subject_details_tab_discussions
import com.wynime.app.ui.lang.subject_details_write_review
import com.wynime.app.ui.rating.EditableRating
import com.wynime.app.ui.rating.EditableRatingDialogsHost
import com.wynime.app.ui.rating.EditableRatingActions
import com.wynime.app.ui.rating.EditableRatingUiState
import com.wynime.app.ui.richtext.RichTextDefaults
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.SubjectProgressState
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import com.wynime.app.ui.subject.details.components.CollectionData
import com.wynime.app.ui.subject.details.components.SeasonTag
import com.wynime.app.ui.subject.details.components.SelectEpisodeButtons
import com.wynime.app.ui.subject.details.components.SubjectBlurredBackground
import com.wynime.app.ui.subject.details.components.SubjectCommentColumn
import com.wynime.app.ui.subject.details.components.SubjectDetailsDefaults
import com.wynime.app.ui.subject.details.components.SubjectDetailsDefaults.MaximumContentWidth
import com.wynime.app.ui.subject.details.components.SubjectDetailsHeader
import com.wynime.app.ui.subject.details.layout.CompactDetailsTabContent
import com.wynime.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import com.wynime.app.ui.subject.details.layout.SubjectDetailsMultiColumnPage
import com.wynime.app.ui.subject.details.layout.SubjectDetailsMultiColumnPlaceholder
import com.wynime.app.ui.subject.details.sections.SubjectCommentsSheet
import com.wynime.app.ui.subject.details.state.SubjectDetailsState
import com.wynime.app.ui.subject.details.state.createTestSubjectDetailsState
import com.wynime.app.ui.subject.details.state.rememberAiringLabelState
import com.wynime.app.ui.subject.details.state.rememberSubjectProgressState
import com.wynime.app.ui.subject.episode.list.EpisodeListDialog
import com.wynime.app.ui.subject.episode.list.EpisodeListItem
import com.wynime.app.ui.subject.person.PeoplePreviewHost
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.app.ui.user.TestSelfInfoUiState
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.toggleCollected
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubjectDetailsScreen(
    vm: SubjectDetailsViewModel,
    onPlay: (episodeId: Int) -> Unit,
    onLoadErrorRetry: () -> Unit,
    onClickTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selfInfo by vm.authState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.load()
    }

    SubjectDetailsScreen(
        state,
        selfInfo,
        onPlay = onPlay,
        onLoadErrorRetry,
        onClickTag,
        { request ->
            scope.launch {
                vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                    toaster.showLoadError(it)
                }
            }
        },
        modifier,
        showTopBar,
        showBlurredBackground,
        windowInsets,
        navigationIcon,
    )
}

@Composable
fun SubjectDetailsScreen(
    state: SubjectDetailsLoadState,
    selfInfo: SelfInfoUiState,
    onPlay: (episodeId: Int) -> Unit,
    onLoadErrorRetry: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val onClickOpenExternal = {
        uriHandler.openUri("https://bgm.tv/subject/${state.subjectId}")
    }

    BoxWithConstraints(modifier) {
        val layoutParams = SubjectDetailsLayoutParams.calculate(maxWidth)
        when (state) {
            is SubjectDetailsLoadState.Placeholder -> PlaceholderSubjectDetailsPage(
                state.subjectInfo,
                layoutParams,
                Modifier,
                showTopBar,
                windowInsets,
                navigationIcon,
                onClickOpenExternal,
            )

            is SubjectDetailsLoadState.Ok -> SubjectDetailsPage(
                state.value,
                selfInfo,
                layoutParams,
                onPlay = onPlay,
                onClickLogin = { navigator.navigateBangumiAuthorize() },
                onClickTag,
                onEpisodeCollectionUpdate = onEpisodeCollectionUpdate,
                Modifier,
                showTopBar,
                showBlurredBackground,
                windowInsets,
                navigationIcon,
                onClickOpenExternal,
            )

            is SubjectDetailsLoadState.Err -> ErrorSubjectDetailsPage(
                state.placeholder,
                error = state.error,
                onRetry = onLoadErrorRetry,
                Modifier,
                showTopBar,
                windowInsets,
                navigationIcon,
                onClickOpenExternal,
            )
        }
    }
}

@Composable
private fun SubjectDetailsPage(
    state: SubjectDetailsState,
    selfInfo: SelfInfoUiState,
    layoutParams: SubjectDetailsLayoutParams,
    onPlay: (episodeId: Int) -> Unit,
    onClickLogin: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val browserNavigator = LocalUriHandler.current
    val navigator = LocalNavigator.current
    val externalAppLinkWarningPrefix = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val openLinkFailedPrefix = stringResource(Lang.foundation_richtext_open_failed_prefix)

    var showSelectEpisode by rememberSaveable { mutableStateOf(false) }

    val imageViewer = rememberImageViewerHandler()
    ImageViewerBackHandler(imageViewer)

    val uiState by state.uiState.collectAsStateWithLifecycle()
    val onEpisodeLongClick: (EpisodeListItem) -> Unit = {
        onEpisodeCollectionUpdate(
            SetEpisodeCollectionTypeRequest(
                uiState.subjectId,
                it.episodeId,
                it.collectionType.toggleCollected(),
            ),
        )
    }

    val onClickCommentUrl = { url: String ->
        RichTextDefaults.checkSanityAndOpen(
            url,
            browserNavigator,
            toaster,
            externalAppLinkWarningPrefix,
            openLinkFailedPrefix,
        )
    }
    val onClickCommentImage = { url: String -> imageViewer.viewImage(url) }

    val coverImageUrl = state.info?.imageLarge?.takeIf { it.isNotBlank() }
    val onClickCover: (() -> Unit)? = coverImageUrl?.let { url -> { imageViewer.viewImage(url) } }

    val onOpenCommentOriginal = { _: UIComment ->
        browserNavigator.openUri("https://bgm.tv/subject/${uiState.subjectId}")
    }

    val themeSettings = LocalThemeSettings.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val onCoverImageSuccess = { success: WynimeImageLoadSuccess ->
        success.bitmap?.let { bitmap = it }
        Unit
    }
    val paletteState = rememberPaletteState()
    LaunchedEffect(themeSettings, bitmap) {
        val bitmap = bitmap ?: return@LaunchedEffect
        if (themeSettings.useDynamicSubjectPageTheme || themeSettings.enableAnimatedGradientSubjectPage) {
            paletteState.generate(bitmap)
        }
    }

    MaterialThemeFromPaletteAndImage(
        if (themeSettings.useDynamicSubjectPageTheme) paletteState.palette else null,
        if (themeSettings.useDynamicSubjectPageTheme) bitmap else null,
    ) {
        if (showSelectEpisode) {
            EpisodeListDialog(
                uiState.episodeListUiState,
                onDismissRequest = { showSelectEpisode = false },
                { navigator.navigateSubjectCaches(uiState.subjectId) },
                { navigator.navigateEpisodeDetails(uiState.subjectId, it.episodeId) },
                onEpisodeLongClick,
            )
        }

        state.subjectCommentReportState?.let { CommentReportHost(it) }

        if (layoutParams.isMultiColumn && state.info != null) {

            var showComments by rememberSaveable { mutableStateOf(false) }
            EditableRatingDialogsHost(uiState.rating, state)
            if (showComments) {
                SubjectCommentsSheet(
                    state = state.subjectCommentState,
                    onClickUrl = onClickCommentUrl,
                    onClickImage = onClickCommentImage,
                    onClickWriteReview = { state.requestEditRating() },
                    onDismissRequest = { showComments = false },
                    reportState = state.subjectCommentReportState,
                    onOpenOriginal = onOpenCommentOriginal,
                )
            }

            PeoplePreviewHost {
                SubjectDetailsMultiColumnPage(
                    state = state,
                    selfInfo = selfInfo,
                    layoutParams = layoutParams,
                    onPlay = onPlay,
                    onEpisodeLongClick = onEpisodeLongClick,
                    onClickTag = onClickTag,
                    onClickLogin = onClickLogin,
                    onShowComments = { showComments = true },
                    onClickCache = { navigator.navigateSubjectCaches(uiState.subjectId) },
                    modifier = modifier,
                    showTopBar = showTopBar,
                    windowInsets = windowInsets,
                    backgroundPalette = if (themeSettings.enableAnimatedGradientSubjectPage) paletteState.palette else null,
                    navigationIcon = navigationIcon,
                    onClickOpenExternal = onClickOpenExternal,
                    onCoverImageSuccess = onCoverImageSuccess,
                    onClickCover = onClickCover,
                )
            }
            return@MaterialThemeFromPaletteAndImage
        }

        val pagerState = rememberPagerState(
            initialPage = SubjectDetailsTab.DETAILS.ordinal,
            pageCount = { 3 },
        )
        val nestedScrollableColumnState = rememberNestedScrollableColumnState()
        SubjectDetailsSingleColumnPage(
            info = state.info,
            seasonTags = {
                SubjectDetailsDefaults.SeasonTag(
                    airDate = state.info?.airDate ?: PackedDate.Invalid,
                    airingLabelState = uiState.rememberAiringLabelState(),
                )
            },
            collectionData = {
                SubjectDetailsDefaults.CollectionData(state.info?.collectionStats ?: SubjectCollectionStats.Zero)
            },
            collectionActions = {
                if (selfInfo.isSessionValid == false) {
                    OutlinedButton(onClickLogin) {
                        Text(stringResource(Lang.subject_details_login_to_collect))
                    }
                } else {
                    EditableSubjectCollectionTypeButton(uiState.collectionTypeEdit, state)
                }
            },
            rating = {
                EditableRating(uiState.rating, state)
            },
            selectEpisodeButton = {
                SubjectDetailsDefaults.SelectEpisodeButtons(
                    uiState.rememberSubjectProgressState(),
                    onShowEpisodeList = { showSelectEpisode = true },
                    onPlay = onPlay,
                )
            },
            modifier = modifier,
            showTopBar = showTopBar,
            showBlurredBackground = showBlurredBackground,
            windowInsets = windowInsets,
            navigationIcon = navigationIcon,
            onCoverImageSuccess = onCoverImageSuccess,
            onClickOpenExternal = onClickOpenExternal,
            onClickCover = onClickCover,
            floatingActionButton = {
                when (SubjectDetailsTab.entries.getOrNull(pagerState.currentPage)) {
                    SubjectDetailsTab.COMMENTS -> {
                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(Lang.subject_details_write_review)) },
                            icon = {
                                Icon(Icons.Rounded.AddComment, null)
                            },
                            onClick = { state.requestEditRating() },
                            expanded = !nestedScrollableColumnState.isHeaderScrolledOut,
                        )
                    }

                    else -> {}
                }
            },
            tabRow = { isOverlay, visible ->
                SubjectDetailsContentTabRow(
                    pagerState,
                    modifier = Modifier.ifThen(!isOverlay) {
                        alpha(if (visible) 1f else 0f)
                    },
                )
            },
            nestedScrollableColumnState = nestedScrollableColumnState,
        ) { contentPadding ->
            SubjectDetailsContentPager(
                pagerState,
                contentPadding,
                detailsTab = { tabContentPadding ->
                    if (state.info == null) return@SubjectDetailsContentPager
                    CompactDetailsTabContent(
                        state = state,
                        info = state.info,
                        onPlay = onPlay,
                        onEpisodeLongClick = onEpisodeLongClick,
                        onClickTag = onClickTag,
                        onShowEpisodeList = { showSelectEpisode = true },
                        onClickCache = { navigator.navigateSubjectCaches(uiState.subjectId) },
                        modifier = Modifier
                            .nestedScrollWorkaround(state.detailsTabLazyListState),
                        listState = state.detailsTabLazyListState,
                        contentPadding = tabContentPadding,
                    )
                },
                commentsTab = { tabContentPadding ->
                    SubjectDetailsDefaults.SubjectCommentColumn(
                        state = state.subjectCommentState,
                        onClickUrl = onClickCommentUrl,
                        onClickImage = onClickCommentImage,
                        reportState = state.subjectCommentReportState,
                        onOpenOriginal = onOpenCommentOriginal,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScrollWorkaround(state.commentTabLazyGridState),
                        gridState = state.commentTabLazyGridState,
                        contentPadding = tabContentPadding,

                        pullToRefreshEnabled = nestedScrollableColumnState.isHeaderFullyVisible,
                    )
                },
                discussionsTab = {
                    LazyColumn(
                        Modifier.fillMaxSize(),

                    ) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Lang.subject_details_coming_soon), Modifier.padding(16.dp))
                            }
                        }
                    }
                },
            )
        }
    }

    ImageViewer(imageViewer) { imageViewer.clear() }
}

@Composable
private fun PlaceholderSubjectDetailsPage(
    subjectInfo: SubjectInfo?,
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    if (layoutParams.isMultiColumn) {

        SubjectDetailsMultiColumnPlaceholder(
            subjectInfo,
            layoutParams,
            modifier,
            showTopBar,
            windowInsets,
            navigationIcon,
            onClickOpenExternal,
        )
        return
    }

    SubjectDetailsSingleColumnPage(
        info = subjectInfo,
        seasonTags = {
            SubjectDetailsDefaults.SeasonTag(
                airDate = remember { PackedDate.Invalid },
                airingLabelState = remember { AiringLabelState(stateOf(null), stateOf(null)) },
                modifier = Modifier.placeholder(true),
            )
        },
        collectionData = {
            SubjectDetailsDefaults.CollectionData(
                remember { SubjectCollectionStats.Zero },
                modifier = Modifier.placeholder(true),
            )
        },
        collectionActions = {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.placeholder(true),
            ) { Text(stringResource(Lang.subject_details_login_to_collect)) }
        },
        rating = {
            EditableRating(
                EditableRatingUiState.Placeholder,
                EditableRatingActions.Noop,
                modifier = Modifier.placeholder(true),
            )
        },
        selectEpisodeButton = {
            SubjectDetailsDefaults.SelectEpisodeButtons(
                remember { SubjectProgressState(stateOf(SubjectProgressInfo.Done)) },
                onShowEpisodeList = { },
                onPlay = { },
                modifier = Modifier.placeholder(true),
            )
        },
        modifier = modifier,
        showTopBar = showTopBar,
        showBlurredBackground = false,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
        onClickOpenExternal = onClickOpenExternal,
    ) { paddingValues ->
        PlaceholderSubjectDetailsContentPager(paddingValues)
    }
}

@Composable
private fun ErrorSubjectDetailsPage(
    subjectInfo: SubjectInfo?,
    error: LoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    SubjectDetailsSingleColumnPage(
        info = subjectInfo,
        seasonTags = { },
        collectionData = { },
        collectionActions = { },
        rating = { },
        selectEpisodeButton = { },
        modifier = modifier,
        showTopBar = showTopBar,
        showBlurredBackground = false,
        windowInsets = windowInsets,
        navigationIcon = navigationIcon,
        onClickOpenExternal = onClickOpenExternal,
    ) { paddingValues ->
        LoadErrorCard(
            error = error,
            onRetry = onRetry,
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding)
                .padding(top = 12.dp),
        )
    }
}

@Composable
fun SubjectDetailsSingleColumnPage(
    info: SubjectInfo?,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionActions: @Composable () -> Unit,
    rating: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    showBlurredBackground: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onCoverImageSuccess: (WynimeImageLoadSuccess) -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    onClickCover: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    tabRow: (@Composable (isOverlay: Boolean, visible: Boolean) -> Unit)? = null,
    nestedScrollableColumnState: NestedScrollableColumnState = rememberNestedScrollableColumnState(),
    content: @Composable NestedScrollableScope.(contentPadding: PaddingValues) -> Unit,
) {
    val backgroundColor = WynimeThemeDefaults.pageContentBackgroundColor
    val stickyTopBarColor = WynimeThemeDefaults.navigationContainerColor
    val topAppBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClickOpenExternal) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
        }
    }

    CompositionLocalProvider(LocalAppChromeHazeState provides rememberHazeState()) {
        val frostedGlassActive = isAppChromeFrostedGlassActive()
        Scaffold(
            topBar = {
                if (showTopBar) {
                    WindowDragArea {

                        TopAppBar(
                            title = {},
                            navigationIcon = navigationIcon,
                            actions = topAppBarActions,
                            colors = WynimeThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                            windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        )
                    }
                }
            },
            modifier = modifier,
            floatingActionButton = floatingActionButton,
            contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            containerColor = backgroundColor,
        ) { scaffoldPadding ->

            val headerContentPadding = scaffoldPadding.only(PaddingValuesSides.Horizontal + PaddingValuesSides.Top)

            val remainingContentPadding = scaffoldPadding.only(PaddingValuesSides.Horizontal)

            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                var tabRowHeightPx by remember { mutableStateOf(0) }

                val density = LocalDensity.current
                val anchorHeightPx = rememberUpdatedState(
                    with(density) { scaffoldPadding.calculateTopPadding().roundToPx() },
                )

                val stickyPanelVisible by remember(nestedScrollableColumnState) {
                    derivedStateOf {
                        val state = nestedScrollableColumnState
                        val threshold = state.headerHeight - tabRowHeightPx - anchorHeightPx.value
                        state.headerHeight > 0 && state.scrolledOffset > 0f && state.scrolledOffset >= threshold
                    }
                }

                NestedScrollableColumn(
                    header = {
                        Column(Modifier.fillMaxWidth()) {
                            Box {

                                if (showBlurredBackground) {
                                    SubjectBlurredBackground(
                                        coverImageUrl = info?.imageLarge,
                                        Modifier.matchParentSize(),
                                        backgroundColor = backgroundColor,
                                    )
                                }

                                Column(
                                    Modifier
                                        .padding(headerContentPadding)
                                        .consumeWindowInsets(headerContentPadding),
                                ) {
                                    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
                                    SubjectDetailsHeader(
                                        info,
                                        info?.imageLarge,
                                        seasonTags = seasonTags,
                                        collectionData = collectionData,
                                        collectionAction = collectionActions,
                                        selectEpisodeButton = selectEpisodeButton,
                                        rating = rating,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentWidth(align = Alignment.CenterHorizontally)
                                            .widthIn(max = MaximumContentWidth)
                                            .fillMaxWidth()
                                            .ifThen(!showTopBar) { padding(top = windowSizeClass.paneVerticalPadding) }
                                            .padding(horizontal = windowSizeClass.paneHorizontalPadding),
                                        onCoverImageSuccess = onCoverImageSuccess,
                                        onClickCover = onClickCover,
                                    )
                                }
                            }

                            if (tabRow != null) {
                                Box(
                                    Modifier
                                        .onSizeChanged { tabRowHeightPx = it.height }
                                        .padding(remainingContentPadding)
                                        .fillMaxWidth(),
                                ) {
                                    tabRow(false, !stickyPanelVisible)
                                }
                            }
                        }
                    },
                    content = {
                        content(remainingContentPadding)
                    },

                    modifier = Modifier
                        .fillMaxSize()
                        .appChromeHazeSource(backgroundColor = backgroundColor),
                    state = nestedScrollableColumnState,
                )

                if (showTopBar || tabRow != null) {

                    val panelBackgroundModifier = Modifier
                        .appChromeFrostedGlass(
                            enabled = frostedGlassActive,
                            containerColor = stickyTopBarColor,
                        )
                        .ifThen(!frostedGlassActive) { background(stickyTopBarColor) }

                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    ) {
                        if (showTopBar) {
                            WynimeAnimatedVisibility(
                                stickyPanelVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                WindowDragArea {
                                    TopAppBar(
                                        title = {
                                            val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
                                            Text(
                                                info?.preferredDisplayName(useOriginalTitle) ?: "",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        modifier = panelBackgroundModifier,
                                        navigationIcon = navigationIcon,
                                        actions = topAppBarActions,
                                        colors = WynimeThemeDefaults.topAppBarColors()
                                            .copy(containerColor = Color.Transparent),
                                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                                    )
                                }
                            }
                        }

                        if (tabRow != null && stickyPanelVisible) {
                            Box(
                                panelBackgroundModifier
                                    .padding(remainingContentPadding)
                                    .fillMaxWidth(),
                            ) {
                                tabRow(true, stickyPanelVisible)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubjectDetailsContentTabRow(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    compact: Boolean = currentWindowAdaptiveInfo1().isWidthCompact
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.widthIn(max = SubjectDetailsDefaults.TabRowWidth),
            indicator = @Composable { tabPositions ->
                TabRowDefaults.PrimaryIndicator(
                    Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                )
            },
            containerColor = Color.Transparent,
            contentColor = TabRowDefaults.secondaryContentColor,
            edgePadding = if (compact) 0.dp else TabRowDefaults.ScrollableTabRowEdgeStartPadding,
            divider = {},
        ) {
            SubjectDetailsTab.entries.forEachIndexed { index, tabId ->
                Tab(
                    selected = pagerState.currentPage == index,
                    modifier = Modifier.widthIn(min = TabRowDefaults.ScrollableTabRowMinTabWidth),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            text = renderSubjectDetailsTab(tabId),
                            softWrap = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SubjectDetailsContentPager(
    pagerState: PagerState,
    contentPadding: PaddingValues,
    detailsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    commentsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    discussionsTab: @Composable (contentPadding: PaddingValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .fillMaxWidth()
            .wrapContentWidth(align = Alignment.CenterHorizontally)
            .widthIn(max = MaximumContentWidth),
    ) {
        HorizontalPager(
            state = pagerState,
            Modifier.fillMaxHeight().touchHorizontalScrollOnly(),
            verticalAlignment = Alignment.Top,
        ) { index ->
            val type = SubjectDetailsTab.entries[index]
            Column(Modifier.padding()) {
                val panePaddingValues =
                    PaddingValues(
                        bottom = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding,
                    ).plus(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues())
                when (type) {
                    SubjectDetailsTab.DETAILS -> detailsTab(panePaddingValues)
                    SubjectDetailsTab.COMMENTS -> commentsTab(panePaddingValues)
                    SubjectDetailsTab.DISCUSSIONS -> discussionsTab(panePaddingValues)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderSubjectDetailsContentPager(paddingValues: PaddingValues) {
    val density = LocalDensity.current
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass

    Column(
        Modifier
            .fillMaxHeight()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues),
    ) {

        Spacer(
            Modifier
                .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(40.dp)
                .placeholder(true),
        )

        Spacer(Modifier.height(16.dp))

        val bodyMediumTextHeight = with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
        val timesDot8TextHeight = (bodyMediumTextHeight.value * 0.8).dp
        val timesDot8TextLinePadding = (bodyMediumTextHeight.value * 0.2).dp

        repeat(5) {
            Spacer(
                Modifier
                    .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                    .padding(bottom = timesDot8TextLinePadding)
                    .fillMaxWidth()
                    .height(timesDot8TextHeight)
                    .placeholder(true, shape = RectangleShape),
            )
        }

        Spacer(Modifier.height(12.dp))

        val labelMediumTextHeight = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }

        FlowRow(
            modifier = Modifier.padding(horizontal = windowSizeClass.paneHorizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(5) {
                Tag(
                    Modifier
                        .height(40.dp)
                        .padding(vertical = 4.dp)
                        .placeholder(true),
                ) {
                    Spacer(
                        Modifier
                            .width(remember { (64..80).random().dp })
                            .height(labelMediumTextHeight),
                    )
                }
            }
        }

        Spacer(Modifier.fillMaxWidth().height(20.dp))

        Spacer(
            Modifier
                .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                .width(48.dp)
                .height(with(density) { MaterialTheme.typography.titleMedium.lineHeight.toDp() })
                .placeholder(true, shape = RectangleShape),
        )

        Spacer(Modifier.fillMaxWidth().height(20.dp))

        @Composable
        fun PlaceholderPersonCard(modifier: Modifier = Modifier) {
            Row(modifier) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .size(48.dp)
                            .placeholder(true),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Spacer(
                            Modifier
                                .width(96.dp)
                                .height(bodyMediumTextHeight)
                                .placeholder(true, shape = RectangleShape),
                        )
                        Spacer(
                            Modifier
                                .width(96.dp)
                                .height(labelMediumTextHeight)
                                .placeholder(true, shape = RectangleShape),
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .padding(horizontal = windowSizeClass.paneHorizontalPadding)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            repeat(4) {
                PlaceholderPersonCard()
            }
        }
    }
}

@Immutable
@Serializable
enum class SubjectDetailsTab {
    DETAILS,
    COMMENTS,
    DISCUSSIONS,
}

sealed interface SubjectDetailsLoadState {
    val subjectId: Int

    data class Placeholder(
        override val subjectId: Int,
        val subjectInfo: SubjectInfo? = null
    ) : SubjectDetailsLoadState

    class Ok(
        override val subjectId: Int,
        val value: SubjectDetailsState
    ) : SubjectDetailsLoadState

    class Err(
        override val subjectId: Int,
        val placeholder: SubjectInfo?,
        val error: LoadError
    ) : SubjectDetailsLoadState
}

@Stable
@Composable
private fun renderSubjectDetailsTab(tab: SubjectDetailsTab): String {
    return when (tab) {
        SubjectDetailsTab.DETAILS -> stringResource(Lang.subject_details_tab_details)
        SubjectDetailsTab.COMMENTS -> stringResource(Lang.subject_details_tab_comments)
        SubjectDetailsTab.DISCUSSIONS -> stringResource(Lang.subject_details_tab_discussions)
    }
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewSubjectDetails() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val state = remember {
        createTestSubjectDetailsState(scope)
            .let { SubjectDetailsLoadState.Ok(it.subjectId, it) }
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewPlaceholderSubjectDetails() = ProvideCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsLoadState.Placeholder(TestSubjectInfo.subjectId, TestSubjectInfo)
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewErrorSubjectDetails() = ProvideCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsLoadState.Err(TestSubjectInfo.subjectId, TestSubjectInfo, LoadError.NetworkError)
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@TestOnly
@Composable
private fun PreviewSubjectDetailsScreen(
    state: SubjectDetailsLoadState,
    modifier: Modifier = Modifier
) {
    SubjectDetailsScreen(
        state,
        TestSelfInfoUiState,
        onPlay = { },
        onLoadErrorRetry = { },
        onClickTag = {},
        {},
        modifier = modifier,
        navigationIcon = { BackNavigationIconButton({}) },
    )
}
