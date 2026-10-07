package com.wynime.app.ui.subject.collection

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.toNavPlaceholder
import com.wynime.app.data.repository.subject.CollectionsFilterQuery
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.ui.adaptive.WynimeTopAppBar
import com.wynime.app.ui.adaptive.WynimeTopAppBarDefaults
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.touchHorizontalScrollOnly
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.session.SelfAvatar
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.appChromeFrostedGlass
import com.wynime.app.ui.foundation.theme.appChromeHazeSource
import com.wynime.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.NsfwMask
import com.wynime.app.ui.foundation.widgets.PullToRefreshBox
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_search
import com.wynime.app.ui.lang.login_sign_in
import com.wynime.app.ui.lang.settings
import com.wynime.app.ui.lang.subject_collection_doing
import com.wynime.app.ui.lang.subject_collection_done
import com.wynime.app.ui.lang.subject_collection_dropped
import com.wynime.app.ui.lang.subject_collection_guest_mode_tip
import com.wynime.app.ui.lang.subject_collection_move_to_watched
import com.wynime.app.ui.lang.subject_collection_on_hold
import com.wynime.app.ui.lang.subject_collection_page_title
import com.wynime.app.ui.lang.subject_collection_syncing
import com.wynime.app.ui.lang.subject_collection_uncollected
import com.wynime.app.ui.lang.subject_collection_wish
import com.wynime.app.ui.search.isLoadingFirstPageOrRefreshing
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.collection.progress.SubjectProgressButton
import com.wynime.app.ui.subject.collection.progress.SubjectProgressStateFactory
import com.wynime.app.ui.subject.collection.progress.rememberSubjectProgressState
import com.wynime.app.ui.subject.episode.list.EpisodeListDialog
import com.wynime.app.ui.subject.episode.list.EpisodeListItem
import com.wynime.app.ui.subject.episode.list.EpisodeListUiState
import com.wynime.app.ui.user.BangumiFullSyncStateDialog
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.platform.hasScrollingBug
import com.wynime.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Stable
val COLLECTION_TABS_SORTED = listOf(
    UnifiedCollectionType.DROPPED,
    UnifiedCollectionType.WISH,
    UnifiedCollectionType.DOING,
    UnifiedCollectionType.ON_HOLD,
    UnifiedCollectionType.DONE,
)

@Stable
class UserCollectionsState(
    private val startSearch: (filterQuery: CollectionsFilterQuery) -> Flow<PagingData<SubjectCollectionInfo>>,
    collectionCountsState: State<SubjectCollectionCounts?>,
    val subjectProgressStateFactory: SubjectProgressStateFactory,
    val createEditableSubjectCollectionTypeState: (subjectCollection: SubjectCollectionInfo) -> EditableSubjectCollectionTypeState,
    private val backgroundScope: CoroutineScope,
    defaultQuery: CollectionsFilterQuery = CollectionsFilterQuery(
        type = UnifiedCollectionType.DOING,
    ),
) {
    private var currentQuery by mutableStateOf(defaultQuery)

    val selectedTypeIndex by derivedStateOf { availableTypes.indexOf(currentQuery.type) }

    val collectionCounts: SubjectCollectionCounts? by collectionCountsState
    val tabRowScrollState = ScrollState(selectedTypeIndex)
    val pagerState = PagerState(selectedTypeIndex) { availableTypes.size }

    private val gridStates = mutableMapOf<Int, LazyGridState>()

    private val cachedLazyPagingItems: MutableMap<Int, LazyPagingItems<SubjectCollectionInfo>> = mutableMapOf()
    val selectedPageRefreshing by derivedStateOf {
        cachedLazyPagingItems[selectedTypeIndex]?.isLoadingFirstPageOrRefreshing == true
    }

    private val restarter = FlowRestarter()

    fun selectTypeIndex(index: Int) {
        currentQuery = currentQuery.copy(type = availableTypes[index])
    }

    fun refreshSelectedPage() {
        cachedLazyPagingItems[selectedTypeIndex]?.refresh()
    }

    @Suppress("INVISIBLE_REFERENCE")
    fun getCollectionLazyPagingItems(typeIndex: Int): LazyPagingItems<SubjectCollectionInfo> {
        return cachedLazyPagingItems.getOrPut(typeIndex) {
            val pagingFlow = flowOf(typeIndex)
                .restartable(restarter)
                .map { CollectionsFilterQuery(availableTypes[it]) }
                .transformLatest { query ->

                    emitAll(startSearch(query))
                }
                .cachedIn(backgroundScope)

            LazyPagingItems(pagingFlow)
        }
    }

    fun refresh() {
        restarter.restart()
    }

    fun getGridState(pageIndex: Int): LazyGridState {
        return gridStates.getOrPut(pageIndex) { LazyGridState() }
    }

    suspend fun scrollToTop() {
        val currentGridState = gridStates[selectedTypeIndex]
        currentGridState?.animateScrollToItem(0)
    }

    companion object {
        private val availableTypes = COLLECTION_TABS_SORTED
    }
}

@Composable
fun CollectionPage(
    state: UserCollectionsState,
    selfInfo: SelfInfoUiState,
    fullSyncState: BangumiSyncState?,
    onClickSearch: () -> Unit,
    onClickLogin: () -> Unit,
    onClickSettings: () -> Unit,
    onFullSync: () -> Unit = {},
    onCollectionUpdate: (subjectId: Int, episode: EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
    enableAnimation: Boolean = true,

    ) {
    val scope = rememberCoroutineScope()
    var hideBangumiSync by rememberSaveable { mutableStateOf(false) }
    val isBangumiSyncing = fullSyncState != null && !fullSyncState.finished

    CollectionPageLayout(
        settingsIcon = {
            if (selfInfo.isSessionValid == false
                || currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium
            ) {
                IconButton(onClick = onClickSettings) {
                    Icon(Icons.Rounded.Settings, stringResource(Lang.settings))
                }
            }
        },
        actions = {
            if (hideBangumiSync && isBangumiSyncing) {
                val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "angle",
                )

                IconButton({ hideBangumiSync = false }) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = stringResource(Lang.subject_collection_syncing),
                        modifier = Modifier.rotate(angle),
                    )
                }
            }
            actions()
        },
        avatar = { recommendedSize ->
            SelfAvatar(
                selfInfo,
                onClick = onClickLogin,
                size = recommendedSize,
            )
        },
        filters = {
            CollectionTypeScrollableTabRow(
                selectedIndex = state.selectedTypeIndex,
                onSelect = { index ->
                    state.selectTypeIndex(index)
                    scope.launch {
                        state.pagerState.animateScrollToPage(index)
                    }
                },
                Modifier.padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding),
                { type ->
                    val size = state.collectionCounts
                    if (size == null) {
                        Text(
                            text = type.displayText(),
                            Modifier.width(IntrinsicSize.Max),
                            softWrap = false,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = type.displayText(),
                                softWrap = false,
                            )
                            Badge(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                Text(
                                    text = size.getCount(type).toString(),
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .wrapContentSize(align = Alignment.Center),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                },
                scrollState = state.tabRowScrollState,
            )
        },
        isRefreshing = { state.selectedPageRefreshing || isBangumiSyncing },
        onRefresh = { state.refreshSelectedPage() },
        onFullSync = onFullSync,
        modifier,
        windowInsets,
    ) { nestedScrollConnection, contentPadding ->
        CollectionPageColumnLayout(
            state,
            modifier = Modifier.fillMaxSize(),
        ) { items, pageIndex ->
            val pullToRefreshState = rememberPullToRefreshState()
            val isPullToRefreshing = items.isLoadingFirstPageOrRefreshing
            PullToRefreshBox(
                isPullToRefreshing,
                onRefresh = { items.refresh() },
                state = pullToRefreshState,
                enabled = !isBangumiSyncing,
                touchOnly = true,
                indicator = {

                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = contentPadding.calculateTopPadding()),
                        isRefreshing = isPullToRefreshing,
                        state = pullToRefreshState,
                    )
                },
            ) {
                SubjectCollectionsColumn(
                    items,
                    item = { collection ->
                        var nsfwModeState: NsfwMode by rememberSaveable(collection) { mutableStateOf(collection.nsfwMode) }
                        val editableSubjectCollectionTypeState = remember(
                            collection.subjectId,
                            collection.collectionType,
                        ) {
                            state.createEditableSubjectCollectionTypeState(collection)
                        }
                        NsfwMask(
                            nsfwModeState,
                            onTemporarilyDisplay = { nsfwModeState = NsfwMode.DISPLAY },
                            shape = SubjectCollectionItemDefaults.shape,
                        ) {
                            SubjectCollectionItem(
                                collection,
                                { onCollectionUpdate(collection.subjectId, it) },
                                state.subjectProgressStateFactory,
                                editableSubjectCollectionTypeState,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    enableAnimation = enableAnimation,
                    gridState = remember(pageIndex) { state.getGridState(pageIndex) },
                    contentPadding = contentPadding,
                )
            }
        }
    }

    if (!hideBangumiSync && fullSyncState != null) {
        BangumiFullSyncStateDialog(
            state = fullSyncState,
            onDismissRequest = { hideBangumiSync = true },
        )
    }
}

@Composable
private fun CollectionPageLayout(
    settingsIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    avatar: @Composable (recommendedSize: DpSize) -> Unit,
    filters: @Composable CollectionPageFilters.() -> Unit,
    isRefreshing: () -> Boolean,
    onRefresh: () -> Unit,
    onFullSync: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
    content: @Composable (nestedScrollConnection: NestedScrollConnection?, contentPadding: PaddingValues) -> Unit,
) {
    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug() || isHeightAtLeastMedium) {
        null
    } else {

        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val frostedGlassActive = isAppChromeFrostedGlassActive()
    val appBarColors = WynimeThemeDefaults.topAppBarColors()
    Scaffold(
        modifier,
        topBar = {

            Column(
                modifier = Modifier.fillMaxWidth()
                    .appChromeFrostedGlass(
                        enabled = frostedGlassActive,
                        containerColor = appBarColors.containerColor,
                    )
                    .ifThen(!frostedGlassActive) { background(appBarColors.containerColor) },
            ) {
                WynimeTopAppBar(
                    title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.subject_collection_page_title)) },
                    modifier = Modifier,
                    actions = {
                        actions()

                        IconButton(
                            onClick = onFullSync,
                            enabled = !isRefreshing(),
                        ) {
                            Icon(
                                Icons.Rounded.Sync,
                                contentDescription = stringResource(Lang.subject_collection_syncing),
                            )
                        }

                        if (LocalPlatform.current.isDesktop()) {

                            IconButton(
                                {
                                    onRefresh()
                                },
                                enabled = !isRefreshing(),
                            ) {
                                Icon(Icons.Rounded.Refresh, null)
                            }
                        }

                        settingsIcon()
                    },
                    avatar = avatar,
                    colors = if (frostedGlassActive) {
                        appBarColors.copy(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        )
                    } else {
                        appBarColors
                    },
                    windowInsets = WynimeWindowInsets.forTopAppBarWithoutDesktopTitle(),
                    scrollBehavior = scrollBehavior,
                    enableFrostedGlass = false,
                )

                filters(CollectionPageFilters)
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        containerColor = WynimeThemeDefaults.pageContentBackgroundColor,
    ) { topBarPaddings ->
        Box(

            Modifier.appChromeHazeSource(backgroundColor = WynimeThemeDefaults.pageContentBackgroundColor)
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth()
                    .widthIn(max = 1300.dp),
            ) {
                content(scrollBehavior?.nestedScrollConnection, topBarPaddings)
            }
        }
    }
}

@Composable
private fun CollectionPageColumnLayout(
    state: UserCollectionsState,
    modifier: Modifier = Modifier,
    content: @Composable (items: LazyPagingItems<SubjectCollectionInfo>, pageIndex: Int) -> Unit,
) {
    LaunchedEffect(state.pagerState.currentPage) {
        if (state.pagerState.currentPage != state.selectedTypeIndex) {
            state.selectTypeIndex(state.pagerState.currentPage)
        }
    }

    HorizontalPager(
        state = state.pagerState,
        modifier = modifier.touchHorizontalScrollOnly(),
        beyondViewportPageCount = 1,
        pageSpacing = 0.dp,
    ) { pageIndex ->
        val items = state
            .getCollectionLazyPagingItems(pageIndex)
            .collectWithLifecycle()

        Column(Modifier.fillMaxSize()) {
            content(items, pageIndex)
        }
    }
}

@Stable
object CollectionPageFilters {
    @Composable
    fun CollectionTypeFilterButtons(
        pagerState: PagerState,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
    ) {
        val uiScope = rememberCoroutineScope()
        SingleChoiceSegmentedButtonRow(modifier) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = pagerState.currentPage == index,
                    onClick = { uiScope.launch { pagerState.scrollToPage(index) } },
                    shape = SegmentedButtonDefaults.itemShape(index, COLLECTION_TABS_SORTED.size),
                    Modifier.wrapContentWidth(),
                ) {
                    itemLabel(type)
                }
            }
        }
    }

    @Composable
    fun CollectionTypeScrollableTabRow(
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
        scrollState: ScrollState = rememberScrollState(),
    ) {
        val widths = remember { mutableStateListOf(*COLLECTION_TABS_SORTED.map { 24.dp }.toTypedArray()) }
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            indicator = @Composable {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false),
                    width = widths[selectedIndex],
                )
            },
            containerColor = Color.Unspecified,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            modifier = modifier.fillMaxWidth(),
            scrollState = scrollState,
        ) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, collectionType ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    text = {
                        val density = LocalDensity.current
                        Box(Modifier.onPlaced { widths[index] = with(density) { it.size.width.toDp() } }) {
                            itemLabel(collectionType)
                        }
                    },
                )
            }
        }
    }

}

@Composable
private fun SubjectCollectionItem(
    subjectCollection: SubjectCollectionInfo,
    onCollectionUpdate: (episode: EpisodeListItem) -> Unit,
    subjectProgressStateFactory: SubjectProgressStateFactory,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    type: UnifiedCollectionType = subjectCollection.collectionType,
    modifier: Modifier = Modifier,
) {
    var showEpisodeProgressDialog by rememberSaveable { mutableStateOf(false) }

    val navigator = LocalNavigator.current
    if (showEpisodeProgressDialog) {
        EpisodeListDialog(
            remember(subjectCollection.episodes) {
                EpisodeListUiState.from(subjectCollection, Clock.System.now())
            },
            onDismissRequest = { showEpisodeProgressDialog = false },
            onCacheClick = {
                navigator.navigateSubjectCaches(subjectCollection.subjectId)
            },
            onEpisodeClick = {
                navigator.navigateEpisodeDetails(
                    subjectCollection.subjectId,
                    it.episodeId,
                )
            },
            onSubjectDetailsClick = {
                navigator.navigateSubjectDetails(
                    subjectCollection.subjectId,
                    placeholder = subjectCollection.subjectInfo.toNavPlaceholder(),
                )
            },
            onCollectionUpdate = onCollectionUpdate,
        )
    }

    val subjectProgressState = subjectProgressStateFactory
        .rememberSubjectProgressState(subjectCollection)

    val scope = rememberCoroutineScope()

    SubjectCollectionItem(
        subjectCollection,
        editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
        onClick = {
            navigator.navigateSubjectDetails(
                subjectCollection.subjectId,
                placeholder = subjectCollection.subjectInfo.toNavPlaceholder(),
            )
        },
        onShowEpisodeList = {
            showEpisodeProgressDialog = true
        },
        playButton = {
            val editableSubjectCollectionTypePresentation by editableSubjectCollectionTypeState.presentationFlow.collectAsStateWithLifecycle()
            val toaster = LocalToaster.current
            if (type != UnifiedCollectionType.DONE) {
                if (subjectProgressState.isDone) {
                    FilledTonalButton(
                        {
                            scope.launch {
                                val error =
                                    editableSubjectCollectionTypeState.setSelfCollectionType(UnifiedCollectionType.DONE)
                                error?.let { toaster.showLoadError(it) }
                            }
                        },
                        enabled = !editableSubjectCollectionTypePresentation.isSetSelfCollectionTypeWorking,
                    ) {
                        Text(
                            stringResource(Lang.subject_collection_move_to_watched),
                            Modifier.requiredWidth(IntrinsicSize.Max),
                            softWrap = false,
                        )
                    }
                } else {
                    SubjectProgressButton(
                        subjectProgressState,
                        onPlay = {
                            subjectProgressState.episodeIdToPlay?.let {
                                navigator.navigateEpisodeDetails(subjectCollection.subjectId, it)
                            }
                        },
                    )
                }
            }
        },
        colors = WynimeThemeDefaults.primaryCardColors(),
        modifier = modifier,
    )
}

@Composable
@Stable
private fun UnifiedCollectionType.displayText(): String {
    return when (this) {
        UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
        UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_doing)
        UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_done)
        UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
        UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_dropped)
        UnifiedCollectionType.NOT_COLLECTED -> stringResource(Lang.subject_collection_uncollected)
    }
}

@Composable
private fun GuestTips(
    onClickSearch: () -> Unit,
    onClickLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(stringResource(Lang.subject_collection_guest_mode_tip))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClickLogin, Modifier.weight(1f)) {
                Icon(Icons.Rounded.HowToReg, null)
                Text(stringResource(Lang.login_sign_in), Modifier.padding(start = 8.dp))
            }

            Button(onClickSearch, Modifier.weight(1f)) {
                Icon(Icons.Rounded.Search, null)
                Text(stringResource(Lang.exploration_search), Modifier.padding(start = 8.dp))
            }
        }
    }
}
