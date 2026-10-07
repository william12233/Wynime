package com.wynime.app.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.ui.adaptive.WynimeListDetailPaneScaffold
import com.wynime.app.ui.adaptive.WynimeTopAppBar
import com.wynime.app.ui.adaptive.WynimeTopAppBarDefaults
import com.wynime.app.ui.adaptive.PaneScope
import com.wynime.app.ui.download.components.DownloadFilterAndSortBar
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.app.ui.download.components.DownloadOverallStats
import com.wynime.app.ui.download.components.DownloadRow
import com.wynime.app.ui.download.components.DownloadSelectionFloatingToolbar
import com.wynime.app.ui.download.components.DownloadSelectionState
import com.wynime.app.ui.download.components.SubjectDownloadGroup
import com.wynime.app.ui.download.components.SubjectDownloadGroupCard
import com.wynime.app.ui.download.components.TestCacheGroupSates
import com.wynime.app.ui.download.components.createTestMediaStats
import com.wynime.app.ui.download.components.rememberDownloadFilterAndSortState
import com.wynime.app.ui.download.components.rememberDownloadSelectionState
import com.wynime.app.ui.download.subject.SubjectDownloadsDetailPane
import com.wynime.app.ui.download.subject.SubjectDownloadsHeader
import com.wynime.app.ui.download.subject.SubjectDownloadsSummaryRow
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.foundation.navigation.BackHandler
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.foundation.rememberCurrentTopAppBarContainerColor
import com.wynime.app.ui.foundation.session.SelfAvatar
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.appChromeHazeSource
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_episode_pause_download
import com.wynime.app.ui.lang.cache_episode_resume_download
import com.wynime.app.ui.lang.cache_management_delete_cache_confirmation
import com.wynime.app.ui.lang.cache_management_delete_cache_title
import com.wynime.app.ui.lang.cache_management_enter_selection_mode
import com.wynime.app.ui.lang.cache_management_exit_selection
import com.wynime.app.ui.lang.cache_management_invalid_cache_info
import com.wynime.app.ui.lang.cache_management_more_info
import com.wynime.app.ui.lang.cache_management_play
import com.wynime.app.ui.lang.cache_management_select_all
import com.wynime.app.ui.lang.cache_management_select_item_for_details
import com.wynime.app.ui.lang.cache_management_selected_count
import com.wynime.app.ui.lang.cache_management_selection_downloading_count
import com.wynime.app.ui.lang.cache_management_selection_summary
import com.wynime.app.ui.lang.cache_management_streaming_not_supported
import com.wynime.app.ui.lang.cache_subject_cancel
import com.wynime.app.ui.lang.cache_subject_delete
import com.wynime.app.ui.lang.downloads_operation_failed
import com.wynime.app.ui.lang.main_screen_page_cache_management
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Immutable
data class DownloadManagementUiState(
    val overallStats: MediaStats,
    val groups: List<SubjectDownloadGroup>,
    val isLoading: Boolean = false,
) {
    internal val entries = groups.flatMap { it.entries }

    companion object {
        val Placeholder = DownloadManagementUiState(
            MediaStats.Unspecified,
            emptyList(),
            isLoading = true,
        )
    }
}

@Composable
fun DownloadManagementScreen(
    vm: DownloadManagementViewModel,
    selfInfo: SelfInfoUiState?,
    onPlay: (DownloadItem) -> Unit,
    onClickLogin: () -> Unit,
    onNavigateCacheDetail: (cacheId: String) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val failedOperations by vm.operationFailures.collectAsStateWithLifecycle()
    if (failedOperations > 0) {
        AlertDialog(
            onDismissRequest = vm::dismissOperationFailures,
            text = { Text(stringResource(Lang.downloads_operation_failed, failedOperations)) },
            confirmButton = { TextButton(onClick = vm::dismissOperationFailures) { Text(stringResource(Lang.cache_subject_cancel)) } },
        )
    }
    DownloadManagementScreen(
        state,
        selfInfo,
        onPlay,
        onResume = { vm.resumeDownload(it) },
        onPause = { vm.pauseDownload(it) },
        onDelete = { vm.deleteDownload(it) },
        onViewDetail = { onNavigateCacheDetail(it.id) },
        onClickLogin = onClickLogin,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        detailPaneContent = { group, selectionState ->

            LaunchedEffect(group?.subjectId) { vm.selectSubject(group?.subjectId, group?.subjectName) }
            val presenter by vm.subjectPresenter.collectAsStateWithLifecycle()
            if (group == null) {
                EmptyDetailPanePlaceholder(Modifier.fillMaxSize())
            } else {
                SubjectDownloadsDetailPane(

                    presenter = presenter?.takeIf { it.subjectId == group.subjectId },
                    loadingTitle = group.subjectName,
                    selectionState = selectionState,
                    onPlay = onPlay,
                    onViewDetail = { onNavigateCacheDetail(it.id) },
                    modifier = Modifier.fillMaxSize(),
                    singlePane = isSinglePane,
                )
            }
        },
    )
}

@Composable
fun DownloadManagementScreen(
    state: DownloadManagementUiState,
    selfInfo: SelfInfoUiState?,
    onPlay: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onPause: (DownloadItem) -> Unit,
    onViewDetail: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    onClickLogin: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
    detailPaneContent: (@Composable PaneScope.(group: SubjectDownloadGroup?, selectionState: DownloadSelectionState) -> Unit)? = null,
) {
    val appBarColors = WynimeThemeDefaults.topAppBarColors()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val listState = rememberLazyListState()
    val cacheFilterState = rememberDownloadFilterAndSortState()
    val selectionState = rememberDownloadSelectionState()

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    val filteredGroups = cacheFilterState.applyFilterAndSortGrouped(state.groups)
    val selectionEntries = remember(filteredGroups) { filteredGroups.flatMap { it.entries } }

    var pendingDeleteEntries by remember { mutableStateOf<List<DownloadItem>?>(null) }

    val selectedEntries = remember(selectionEntries, selectionState.selectedIds) {
        selectionEntries.filter { it.id in selectionState.selectedIds }
    }

    val selectionCount = selectionState.selectedIds.size

    LaunchedEffect(selectionEntries, selectionState.inSelection, state.isLoading) {
        if (selectionState.inSelection && !state.isLoading) {
            val validIds = selectionEntries.mapTo(hashSetOf()) { it.id }
            val remaining = selectionState.selectedIds.filter { id -> id in validIds }.toSet()
            if (remaining.isEmpty() && selectionState.selectedIds.isNotEmpty()) selectionState.clear()
            else selectionState.overrideSelected(remaining)
        }
    }

    val isSinglePaneDetailVisible = navigator.scaffoldValue.let { value ->
        value[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Expanded &&
                value[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    }

    var currentViewingGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.groups, state.isLoading) {
        if (state.isLoading) return@LaunchedEffect
        if (state.groups.isEmpty()) {
            currentViewingGroupKey = null
        } else if (state.groups.none { it.key == currentViewingGroupKey }) {
            currentViewingGroupKey = state.groups.first().key
        }
    }
    val currentViewingGroup = remember(state.groups, currentViewingGroupKey) {
        state.groups.firstOrNull { it.key == currentViewingGroupKey }
    }

    val selectAllScopeEntries = if (isSinglePaneDetailVisible) {
        currentViewingGroup?.entries.orEmpty()
    } else {
        selectionEntries
    }

    val allSelected = remember(selectAllScopeEntries, selectionState.selectedIds) {
        selectAllScopeEntries.isNotEmpty() &&
                selectAllScopeEntries.all { it.id in selectionState.selectedIds }
    }

    pendingDeleteEntries?.let { entries ->
        DeleteActionDialog(
            onDismiss = { pendingDeleteEntries = null },
            confirmEnabled = state.groups.flatMap { it.entries }.none { current ->
                current.isBusy && entries.any { it.id == current.id }
            },
            onConfirm = {
                entries.forEach(onDelete)
                pendingDeleteEntries = null
            },
        )
    }

    val tasker = rememberAsyncHandler()

    Scaffold(
        modifier = modifier,
        topBar = {
            DownloadManagementTopBar(
                selectionMode = selectionState.inSelection,
                selectionCount = selectionCount,
                allSelected = allSelected,
                hasEntries = selectAllScopeEntries.isNotEmpty(),
                onEnterSelection = { selectionState.enterSelectionWith(emptySet()) },
                onExitSelection = { selectionState.clear() },
                onToggleSelectAll = {
                    val scopeIds = selectAllScopeEntries.map { it.id }
                    selectionState.enterSelectionWith(
                        if (allSelected) {
                            selectionState.selectedIds - scopeIds.toSet()
                        } else {
                            selectionState.selectedIds + scopeIds
                        },
                    )
                },
                selfInfo = selfInfo,
                onClickLogin = onClickLogin,
                navigationIcon = navigationIcon,
                appBarColors = appBarColors,
                windowInsets = WynimeWindowInsets.forTopAppBarWithoutDesktopTitle(),
                scrollBehavior = scrollBehavior,
                detailPaneTitle = if (isSinglePaneDetailVisible) currentViewingGroup?.subjectName else null,
                onNavigateBackFromDetail = { tasker.launch { navigator.navigateBack() } },
            )
        },
        bottomBar = {
            WynimeAnimatedVisibility(selectionState.inSelection) {
                DownloadSelectionFloatingToolbar(
                    resumeEnabled = selectedEntries.none { it.isBusy } && selectedEntries.any { !it.isFinished && it.isPaused },
                    pauseEnabled = selectedEntries.none { it.isBusy } && selectedEntries.any { !it.isFinished && !it.isPaused && !it.isFailed },
                    deleteEnabled = selectedEntries.isNotEmpty() && selectedEntries.none { it.isBusy },
                    onResumeSelected = {
                        selectedEntries.forEach(onResume)
                    },
                    onPauseSelected = {
                        selectedEntries.forEach(onPause)
                    },
                    onDeleteSelected = { pendingDeleteEntries = selectedEntries.toList() },
                    windowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
            }
        },
        containerColor = WynimeThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current

        val listBottomPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding())
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        val paneExtraPadding = windowSizeClass.paneHorizontalPadding
        WynimeListDetailPaneScaffold(

            modifier = Modifier
                .appChromeHazeSource(backgroundColor = WynimeThemeDefaults.pageContentBackgroundColor)
                .padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                )

                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = 1200.dp),
            navigator = navigator,
            listPaneTopAppBar = null,
            listPaneContent = {
                DownloadGroupCardsList(
                    state = state,
                    filteredGroups = filteredGroups,
                    selectionEntries = selectionEntries,
                    selectedEntries = selectedEntries,
                    selectionState = selectionState,
                    cacheFilterState = cacheFilterState,
                    appBarColors = appBarColors,
                    scrollBehavior = scrollBehavior,
                    listState = listState,
                    listBottomPadding = listBottomPadding,
                    paneExtraPadding = paneExtraPadding,
                    highlightSelectedGroupKey = if (isSinglePane) null else currentViewingGroupKey,
                    onClickGroup = { group ->

                        currentViewingGroupKey = group.key
                        tasker.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                        }
                    },
                    onToggleGroupSelection = { group ->
                        selectionState.toggleSelection(*group.entries.map { it.id }.toTypedArray())
                    },
                    onEnterGroupSelection = { group ->
                        selectionState.enterSelectionWith(selectionState.selectedIds + group.entries.map { it.id })
                    },
                )
            },
            detailPane = {
                Column(
                    Modifier
                        .paneContentPadding(extraStart = (-16).dp, extraEnd = (-16).dp)
                        .paneWindowInsetsPadding()
                        .padding(listBottomPadding)

                        .then(
                            if (isSinglePane) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
                        )
                        .fillMaxSize(),
                ) {
                    if (detailPaneContent != null) {
                        detailPaneContent(currentViewingGroup, selectionState)
                    } else {
                        DefaultDownloadGroupDetailPane(
                            group = currentViewingGroup,
                            selectionState = selectionState,
                            onPlay = onPlay,
                            onResume = onResume,
                            onPause = onPause,
                            onDelete = onDelete,
                            onViewDetail = onViewDetail,
                            modifier = Modifier.fillMaxSize(),
                            singlePane = isSinglePane,
                        )
                    }
                }
            },

            contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
            useSharedTransition = false,
            listPanePreferredWidth = preferredListPaneWidth(),

            minListPaneWidth = preferredListPaneWidth(),
        )

        BackHandler(selectionState.inSelection) { selectionState.clear() }
    }
}

@Composable
private fun preferredListPaneWidth(): Dp {
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(1600) -> 400.dp
        windowSizeClass.isWidthAtLeastBreakpoint(1200) -> 412.dp
        windowSizeClass.isWidthAtLeastBreakpoint(840) -> 360.dp
        else -> (((windowSizeClass.minWidthDp - 24 * 3).toFloat() / 2).dp).coerceAtLeast(360.dp)
    }
}

@Composable
private fun PaneScope.DownloadGroupCardsList(
    state: DownloadManagementUiState,
    filteredGroups: List<SubjectDownloadGroup>,
    selectionEntries: List<DownloadItem>,
    selectedEntries: List<DownloadItem>,
    selectionState: DownloadSelectionState,
    cacheFilterState: com.wynime.app.ui.download.components.DownloadFilterAndSortState,
    appBarColors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior,
    listState: LazyListState,
    listBottomPadding: PaddingValues,
    paneExtraPadding: Dp,
    highlightSelectedGroupKey: String?,
    onClickGroup: (SubjectDownloadGroup) -> Unit,
    onToggleGroupSelection: (SubjectDownloadGroup) -> Unit,
    onEnterGroupSelection: (SubjectDownloadGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAppBarContainerColor by rememberCurrentTopAppBarContainerColor(appBarColors, scrollBehavior)
    LazyColumn(
        modifier = modifier
            .paneWindowInsetsPadding()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxWidth(),
        state = listState,
        contentPadding = listBottomPadding + PaddingValues(bottom = paneExtraPadding),
    ) {
        item("overall_stats") {
            Surface(
                color = appBarColors.containerColor,
                contentColor = contentColorFor(appBarColors.containerColor),
            ) {
                if (selectionState.inSelection) {
                    DownloadSelectionSummary(
                        selectedEntries,
                        Modifier
                            .paneContentPadding()
                            .fillMaxWidth(),
                    )
                } else {
                    DownloadOverallStats(
                        { state.overallStats },
                        Modifier
                            .paneContentPadding()
                            .fillMaxWidth(),
                    )
                }
            }
        }
        stickyHeader("filter_row") {
            DownloadFilterAndSortBar(
                state = cacheFilterState,
                modifier = Modifier.paneContentPadding().fillMaxWidth().padding(vertical = 8.dp),
                containerColor = topAppBarContainerColor,
                mediaCacheEngineOptions = remember(state.entries) {
                    state.entries.mapNotNull { it.engineKey }.distinct()
                },
            )
        }
        items(filteredGroups, key = { it.key }) { group ->
            SubjectDownloadGroupCard(
                group = group,
                selected = group.key == highlightSelectedGroupKey,
                selectionMode = selectionState.inSelection,
                allEntriesSelected = group.entries.all { it.id in selectionState.selectedIds },
                onToggleGroupSelection = { onToggleGroupSelection(group) },
                onLongClick = { onEnterGroupSelection(group) },
                onClick = {
                    if (selectionState.inSelection) {
                        onToggleGroupSelection(group)
                    } else {
                        onClickGroup(group)
                    }
                },

                modifier = if (isSinglePane) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.padding(horizontal = 8.dp).fillMaxWidth()
                },
                showChevron = isSinglePane,
                shape = if (isSinglePane) RectangleShape else MaterialTheme.shapes.large,
            )
        }
    }
}

@Composable
private fun DefaultDownloadGroupDetailPane(
    group: SubjectDownloadGroup?,
    selectionState: DownloadSelectionState,
    onPlay: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onPause: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    onViewDetail: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,

    singlePane: Boolean = false,
) {
    if (group == null) {
        EmptyDetailPanePlaceholder(modifier)
        return
    }
    val onPauseAll = {
        group.entries.forEach(onPause)
    }
    val onResumeAll = {
        group.entries.forEach(onResume)
    }
    val rowShape = if (singlePane) RectangleShape else MaterialTheme.shapes.medium
    LazyColumn(
        modifier,

        contentPadding = if (singlePane) PaddingValues(0.dp) else PaddingValues(top = 16.dp),
        verticalArrangement = if (singlePane) Arrangement.Top else Arrangement.spacedBy(8.dp),
    ) {
        item("detail_header") {
            if (singlePane) {
                SubjectDownloadsSummaryRow(
                    downloads = group.entries,
                    totalEpisodeCount = group.totalEpisodeCount,
                    inSelection = selectionState.inSelection,
                    selectedEntries = group.entries.filter { it.id in selectionState.selectedIds },
                    onPauseAll = onPauseAll,
                    onResumeAll = onResumeAll,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SubjectDownloadsHeader(
                    title = group.subjectName,
                    downloads = group.entries,
                    totalEpisodeCount = group.totalEpisodeCount,
                    onPauseAll = onPauseAll,
                    onResumeAll = onResumeAll,
                )
            }
        }
        items(group.entries, key = { it.id }) { entry ->
            DownloadRow(
                episode = entry,
                mediaSourceInfoProvider = null,
                selectionMode = selectionState.inSelection,
                selected = entry.id in selectionState.selectedIds,
                onToggleSelected = { selectionState.toggleSelection(entry.id) },
                onEnterSelection = {
                    selectionState.enterSelectionWith(selectionState.selectedIds + entry.id)
                },
                onPlay = { onPlay(entry) },
                onResume = { onResume(entry) },
                onPause = { onPause(entry) },
                onDelete = { onDelete(entry) },
                onViewDetail = { onViewDetail(entry) },
                shape = rowShape,
            )
        }
    }
}

@Composable
private fun EmptyDetailPanePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Lang.cache_management_select_item_for_details))
    }
}

@Composable
private fun DownloadManagementTopBar(
    selectionMode: Boolean,
    selectionCount: Int,
    allSelected: Boolean,
    hasEntries: Boolean,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    selfInfo: SelfInfoUiState?,
    onClickLogin: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    appBarColors: TopAppBarColors,
    windowInsets: WindowInsets,
    scrollBehavior: TopAppBarScrollBehavior?,

    detailPaneTitle: String? = null,
    onNavigateBackFromDetail: () -> Unit = {},
) {
    if (!selectionMode && detailPaneTitle != null) {
        WynimeTopAppBar(
            title = { WynimeTopAppBarDefaults.Title(detailPaneTitle) },
            navigationIcon = { BackNavigationIconButton(onNavigateBackFromDetail) },
            avatar = { },
            colors = appBarColors,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
        return
    }
    if (selectionMode) {
        val selectedCountText = stringResource(Lang.cache_management_selected_count, selectionCount)
        val exitSelectionText = stringResource(Lang.cache_management_exit_selection)
        val selectAllText = stringResource(Lang.cache_management_select_all)
        WynimeTopAppBar(
            title = { WynimeTopAppBarDefaults.Title(selectedCountText) },
            navigationIcon = {
                IconButton(onClick = onExitSelection) { Icon(Icons.Rounded.Close, exitSelectionText) }
            },
            actions = {
                IconButton(
                    onClick = onToggleSelectAll,
                    enabled = hasEntries,
                ) {
                    Icon(
                        if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        selectAllText,
                    )
                }
            },
            avatar = { },
            colors = appBarColors,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
    } else {
        WynimeTopAppBar(
            title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.main_screen_page_cache_management)) },
            navigationIcon = navigationIcon,
            actions = {
                val enterSelectionModeText = stringResource(Lang.cache_management_enter_selection_mode)
                IconButton(
                    onClick = onEnterSelection,
                    enabled = hasEntries,
                ) {
                    Icon(Icons.Default.Checklist, enterSelectionModeText)
                }
            },
            avatar = selfInfo?.let {
                { recommendedSize ->
                    SelfAvatar(
                        state = it,
                        onClick = onClickLogin,
                        size = recommendedSize,
                    )
                }
            } ?: { },
            colors = appBarColors,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun DownloadSelectionSummary(
    selectedEntries: List<DownloadItem>,
    modifier: Modifier = Modifier,
) {
    val totalSize = remember(selectedEntries) {
        selectedEntries.fold(0L) { acc, entry -> acc + entry.totalSize.inBytes }.bytes
    }
    val downloadingCount = remember(selectedEntries) {
        selectedEntries.count { !it.isFinished && !it.isPaused && !it.isFailed }
    }
    val summaryText = stringResource(Lang.cache_management_selection_summary, selectedEntries.size, "$totalSize")
    val downloadingText = stringResource(Lang.cache_management_selection_downloading_count, downloadingCount)
    Text(
        if (downloadingCount > 0) "$summaryText · $downloadingText" else summaryText,
        modifier.padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

object DownloadManagementTestTags {
    const val DELETE_CONFIRM_BUTTON = "cache_management_delete_confirm_button"
}

@Composable
internal fun DeleteActionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Lang.cache_management_delete_cache_title)) },
        text = { Text(stringResource(Lang.cache_management_delete_cache_confirmation)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.testTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON),
            ) { Text(stringResource(Lang.cache_subject_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onDismiss) { Text(stringResource(Lang.cache_subject_cancel)) }
        },
    )
}

@Composable
internal fun DownloadActionDropdown(
    show: Boolean,
    onDismiss: () -> Unit,
    episode: DownloadItem,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onViewDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
) {
    val toaster = LocalToaster.current
    val resumeDownloadText = stringResource(Lang.cache_episode_resume_download)
    val pauseDownloadText = stringResource(Lang.cache_episode_pause_download)
    val playText = stringResource(Lang.cache_management_play)
    val invalidCacheInfoText = stringResource(Lang.cache_management_invalid_cache_info)
    val streamingNotSupportedText = stringResource(Lang.cache_management_streaming_not_supported)
    val moreInfoText = stringResource(Lang.cache_management_more_info)
    DropdownMenu(
        expanded = show,
        onDismissRequest = onDismiss,
        modifier = modifier,
        offset = offset,
    ) {
        if (!episode.isFinished) {
            if (episode.isPaused) {
                DropdownMenuItem(
                    text = { Text(resumeDownloadText) },
                    enabled = !episode.isBusy,
                    leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                    onClick = {
                        onResume()
                        onDismiss()
                    },
                )
            } else if (!episode.isFailed) {
                DropdownMenuItem(
                    text = { Text(pauseDownloadText) },
                    enabled = !episode.isBusy,
                    leadingIcon = { Icon(Icons.Rounded.Pause, null) },
                    onClick = {
                        onPause()
                        onDismiss()
                    },
                )
            }
        }
        if (!episode.isFailed) {
            DropdownMenuItem(
                text = { Text(playText) },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = {
                    when (episode.playability) {
                        DownloadItem.Playability.PLAYABLE -> {
                            onPlay()
                            onDismiss()
                        }

                        DownloadItem.Playability.INVALID_SUBJECT_EPISODE_ID -> {
                            toaster.toast(invalidCacheInfoText)
                        }

                        DownloadItem.Playability.STREAMING_NOT_SUPPORTED -> {
                            toaster.toast(streamingNotSupportedText)
                        }
                    }
                },
            )
        }
        onViewDetail?.let {
            DropdownMenuItem(
                text = { Text(moreInfoText) },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                onClick = {
                    it()
                    onDismiss()
                },
            )
        }

        DropdownMenuItem(
            text = { Text(stringResource(Lang.cache_subject_delete), color = MaterialTheme.colorScheme.error) },
            enabled = !episode.isBusy,
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
                onDelete()
                onDismiss()
            },
        )
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewDownloadManagementScreen() {
    ProvideCompositionLocalsForPreview {
        DownloadManagementScreen(
            state = remember {
                DownloadManagementUiState(
                    createTestMediaStats(),
                    TestCacheGroupSates,
                )
            },
            selfInfo = null,
            onPlay = { },
            onResume = {},
            onPause = {},
            onDelete = {},
            onClickLogin = { },
            onViewDetail = { },
            navigationIcon = { BackNavigationIconButton({ }) },
        )
    }
}
