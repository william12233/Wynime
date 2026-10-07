package com.wynime.app.ui.exploration

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.wynime.app.data.models.recommend.RecommendedItemInfo
import com.wynime.app.data.models.recommend.RecommendedSubjectInfo
import com.wynime.app.data.models.recommend.TestRecommendedItemInfos
import com.wynime.app.data.models.subject.FollowedSubjectInfo
import com.wynime.app.data.models.subject.TestFollowedSubjectInfos
import com.wynime.app.data.models.subject.subjectInfo
import com.wynime.app.data.models.subject.toNavPlaceholder
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.navigation.SubjectDetailPlaceholder
import com.wynime.app.ui.adaptive.WynimeTopAppBar
import com.wynime.app.ui.adaptive.WynimeTopAppBarDefaults
import com.wynime.app.ui.adaptive.HorizontalScrollControlScaffoldOnDesktop
import com.wynime.app.ui.adaptive.NavTitleHeader
import com.wynime.app.ui.exploration.followed.FollowedSubjectsDefaults
import com.wynime.app.ui.exploration.followed.FollowedSubjectsLazyRow
import com.wynime.app.ui.exploration.recommend.RecommendationDefaults
import com.wynime.app.ui.exploration.recommend.recommendationItems
import com.wynime.app.ui.exploration.today.TestTodayUpdateSubjectInfos
import com.wynime.app.ui.exploration.today.TodayUpdatesCarousel
import com.wynime.app.ui.foundation.HorizontalScrollControlState
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.ifNotNullThen
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.CarouselItemDefaults
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.foundation.rememberHorizontalScrollControlState
import com.wynime.app.ui.foundation.session.SelfAvatar
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.appChromeHazeSource
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_continue_watching
import com.wynime.app.ui.lang.exploration_horizontal_scroll_tip
import com.wynime.app.ui.lang.exploration_recommendations
import com.wynime.app.ui.lang.exploration_schedule
import com.wynime.app.ui.lang.exploration_search
import com.wynime.app.ui.lang.exploration_settings
import com.wynime.app.ui.lang.exploration_title
import com.wynime.app.ui.lang.exploration_today_updates
import com.wynime.app.ui.search.createTestPager
import com.wynime.app.ui.search.rememberLoadErrorState
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.app.ui.user.TestSelfInfoUiState
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import com.wynime.utils.analytics.recordEvent
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.hasScrollingBug
import org.jetbrains.compose.resources.stringResource

@Stable
class ExplorationPageState(
    val todayUpdatesState: StateFlow<TodayUpdatesUiState>,
    private val onRetryTodayUpdates: () -> Unit,
    val followedSubjectsPager: Flow<PagingData<FollowedSubjectInfo>>,
    val recommendationPager: Flow<PagingData<RecommendedItemInfo>>,
    val horizontalScrollTipFlow: Flow<Boolean>,
    private val onSetDisableHorizontalScrollTip: () -> Unit,
) {
    val followedSubjectsLazyRowState = LazyListState()

    val pageScrollState = LazyGridState()

    fun setDisableHorizontalScrollTip() {
        onSetDisableHorizontalScrollTip()
    }

    fun retryTodayUpdates() {
        onRetryTodayUpdates()
    }
}

@Composable
fun ExplorationScreen(
    state: ExplorationPageState,
    selfInfo: SelfInfoUiState,
    onSearch: () -> Unit,
    onClickLogin: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
) {
    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug() || isHeightAtLeastMedium) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {

        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WynimeThemeDefaults.pageContentBackgroundColor,
        topBar = {
            WynimeTopAppBar(
                title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.exploration_title)) },
                Modifier.fillMaxWidth(),
                actions = {
                    actions()
                    if (selfInfo.isSessionValid == false
                        || currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium
                    ) {
                        IconButton(onClick = onClickSettings) {
                            Icon(Icons.Rounded.Settings, stringResource(Lang.exploration_settings))
                        }
                    }
                },
                avatar = { recommendedSize ->
                    SelfAvatar(
                        selfInfo,
                        onClick = onClickLogin,
                        size = recommendedSize,
                    )
                },
                searchIconButton = {
                    IconButton(onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(Lang.exploration_search))
                    }
                },
                searchBar = {
                    IconButton(onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(Lang.exploration_search))
                    }
                },
                windowInsets = WynimeWindowInsets.forTopAppBarWithoutDesktopTitle(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { topBarPadding ->
        val horizontalPadding = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding
        val horizontalContentPadding =
            PaddingValues(horizontal = horizontalPadding)

        val navigator = LocalNavigator.current
        val density = LocalDensity.current
        val showHorizontalNavigateTip by state.horizontalScrollTipFlow.collectAsState(false)
        val toaster = LocalToaster.current
        val scope = rememberCoroutineScope()
        val horizontalScrollTip = stringResource(Lang.exploration_horizontal_scroll_tip)

        val todayUpdatesState by state.todayUpdatesState.collectAsState()
        val todayUpdatesCarouselState = rememberCarouselState(initialItem = 0) {
            when (val current = todayUpdatesState) {
                TodayUpdatesUiState.InitialLoading -> 8
                is TodayUpdatesUiState.Content -> current.items.size
                is TodayUpdatesUiState.Error -> 0
            }
        }
        val recommendationPager = state.recommendationPager.collectAsLazyPagingItemsWithLifecycle()
        val recommendationPagerLoadError by recommendationPager.rememberLoadErrorState()
        val wynimeMotionScheme = LocalWynimeMotionScheme.current
        val layoutParams = RecommendationDefaults.layoutParameters()
        LazyVerticalGrid(
            layoutParams.gridCells,
            Modifier

                .appChromeHazeSource(backgroundColor = WynimeThemeDefaults.pageContentBackgroundColor)
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = 1300.dp)
                .fillMaxSize()
                .ifNotNullThen(scrollBehavior) {
                    nestedScroll(it.nestedScrollConnection)
                },
            state = state.pageScrollState,
            contentPadding = topBarPadding + PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = layoutParams.horizontalArrangement,
            verticalArrangement = layoutParams.verticalArrangement,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    NavTitleHeader(
                        title = { Text(stringResource(Lang.exploration_today_updates), softWrap = false) },
                        trailingActions = {
                            TextButton(
                                { navigator.navigateSchedule() },
                                Modifier,
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(Lang.exploration_schedule), softWrap = false)
                            }
                        },
                    )

                    val carouselItemSize = CarouselItemDefaults.itemSize()
                    HorizontalScrollControlScaffoldOnDesktop(
                        rememberHorizontalScrollControlState(
                            todayUpdatesCarouselState,
                            onClickScroll = { direction ->
                                scope.launch {
                                    todayUpdatesCarouselState.animateScrollBy(
                                        with<Density, Float>(density) { (carouselItemSize.preferredWidth * 2).toPx() } *
                                                if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1,
                                    )
                                }
                                if (showHorizontalNavigateTip) {
                                    toaster.toast(horizontalScrollTip)
                                    state.setDisableHorizontalScrollTip()
                                }
                            },
                        ),
                    ) {
                        TodayUpdatesCarousel(
                            items = (todayUpdatesState as? TodayUpdatesUiState.Content)?.items.orEmpty(),
                            isInitialLoading = todayUpdatesState is TodayUpdatesUiState.InitialLoading,
                            error = (todayUpdatesState as? TodayUpdatesUiState.Error)?.error,
                            onRetry = state::retryTodayUpdates,
                            onClick = {
                                Analytics.recordEvent(SubjectEnter) {
                                    put("source", "home_today_updates")
                                    put("subject_id", it.bangumiId)
                                }
                                navigator.navigateSubjectDetails(
                                    subjectId = it.bangumiId,
                                    placeholder = SubjectDetailPlaceholder(
                                        id = it.bangumiId,
                                        name = it.displayName,
                                        coverUrl = it.imageLarge,
                                    ),
                                )
                            },
                            contentPadding = PaddingValues(vertical = 8.dp),
                            carouselState = todayUpdatesCarouselState,
                        )
                    }

                    NavTitleHeader(
                        title = { Text(stringResource(Lang.exploration_continue_watching), softWrap = false) },
                    )

                    val followedSubjectsPager =
                        state.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
                    val followedSubjectsLayoutParameters =
                        FollowedSubjectsDefaults.layoutParameters(currentWindowAdaptiveInfo1())

                    HorizontalScrollControlScaffoldOnDesktop(
                        rememberHorizontalScrollControlState(
                            state.followedSubjectsLazyRowState,
                            onClickScroll = { direction ->
                                scope.launch {
                                    state.followedSubjectsLazyRowState.animateScrollBy(
                                        with<Density, Float>(density) { (followedSubjectsLayoutParameters.imageSize.height * 2).toPx() } *
                                                if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1,
                                    )
                                }
                                if (showHorizontalNavigateTip) {
                                    toaster.toast(horizontalScrollTip)
                                    state.setDisableHorizontalScrollTip()
                                }
                            },
                        ),
                    ) {
                        FollowedSubjectsLazyRow(
                            followedSubjectsPager,
                            onClick = {
                                Analytics.recordEvent(SubjectEnter) {
                                    put("source", "home_followed")
                                    put("subject_id", it.subjectInfo.subjectId)
                                }
                                navigator.navigateSubjectDetails(
                                    subjectId = it.subjectInfo.subjectId,
                                    placeholder = it.subjectInfo.toNavPlaceholder(),
                                )
                            },
                            onPlay = {
                                it.subjectProgressInfo.nextEpisodeIdToPlay?.let<Int, Unit> { it1 ->
                                    navigator.navigateEpisodeDetails(
                                        it.subjectInfo.subjectId,
                                        it1,
                                    )
                                }
                            },
                            layoutParameters = followedSubjectsLayoutParameters,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            lazyListState = state.followedSubjectsLazyRowState,
                        )
                    }

                    NavTitleHeader(
                        title = { Text(stringResource(Lang.exploration_recommendations), softWrap = false) },
                    )
                }
            }

            recommendationItems(
                recommendationPager,
                loadError = recommendationPagerLoadError,
                onClick = { info ->
                    when (info) {
                        is RecommendedSubjectInfo -> {
                            Analytics.recordEvent(SubjectEnter) {
                                put("source", "home_recommendation")
                                put("subject_id", info.bangumiId)
                            }
                            navigator.navigateSubjectDetails(
                                subjectId = info.bangumiId,
                                placeholder = info.toNavPlaceholder(),
                            )
                        }
                    }
                },
                layoutParams,
            )
        }
    }
}

fun RecommendedSubjectInfo.toNavPlaceholder(): SubjectDetailPlaceholder {
    return SubjectDetailPlaceholder(
        id = bangumiId,
        name = name,
        nameCN = nameCn,
        coverUrl = imageLarge,
    )
}

@OptIn(TestOnly::class)
@Composable
@PreviewScreenSizes
@PreviewLightDark
private fun PreviewExplorationPage() {
    ProvideCompositionLocalsForPreview {
        ExplorationScreen(
            remember {
                ExplorationPageState(
                    todayUpdatesState = MutableStateFlow(TodayUpdatesUiState.Content(TestTodayUpdateSubjectInfos)),
                    onRetryTodayUpdates = {},
                    followedSubjectsPager = createTestPager(TestFollowedSubjectInfos),
                    recommendationPager = createTestPager(TestRecommendedItemInfos),
                    horizontalScrollTipFlow = flowOf(false),
                    onSetDisableHorizontalScrollTip = {},
                )
            },
            selfInfo = TestSelfInfoUiState,
            {},
            {},
            {},
        )
    }
}
