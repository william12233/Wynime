package com.wynime.app.ui.exploration.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.collectLatest
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.domain.search.SearchSort
import com.wynime.app.ui.foundation.IconButton
import com.wynime.app.ui.foundation.animation.WynimeMotionScheme
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.icons.BackgroundDotLarge
import com.wynime.app.ui.foundation.icons.GalleryThumbnail
import com.wynime.app.ui.foundation.interaction.keyboardDirectionToSelectItem
import com.wynime.app.ui.foundation.interaction.keyboardPageToScroll
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.layout.paneVerticalPadding
import com.wynime.app.ui.foundation.widgets.NsfwMask
import com.wynime.app.ui.foundation.widgets.SelectableDropdownMenuItem
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_search_results_shown
import com.wynime.app.ui.lang.exploration_search_sort_collection
import com.wynime.app.ui.lang.exploration_search_sort_date
import com.wynime.app.ui.lang.exploration_search_sort_match
import com.wynime.app.ui.lang.exploration_search_sort_rank
import com.wynime.app.ui.lang.foundation_load_error_no_results
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.ui.search.SearchDefaults.IconTextButton
import com.wynime.app.ui.search.SearchResultLazyVerticalGrid
import com.wynime.app.ui.search.hasFirstPage
import com.wynime.app.ui.search.isFinishedAndEmpty
import com.wynime.app.ui.subject.SubjectCoverCard
import com.wynime.app.ui.subject.SubjectGridDefaults
import com.wynime.app.ui.subject.SubjectGridLayoutParams
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchResultColumn(
    items: LazyPagingItems<SubjectPreviewItemInfo>,
    layoutKind: SearchResultLayoutKind,
    summary: @Composable SearchResultColumnScope.() -> Unit,
    selectedItemIndex: () -> Int,
    onSelect: (index: Int) -> Unit,
    onPlay: (info: SubjectPreviewItemInfo) -> Unit,
    highlightSelected: Boolean = true,
    modifier: Modifier = Modifier,
    headers: LazyGridScope.() -> Unit = {},
    state: LazyGridState = rememberLazyGridState(),
    layoutParams: SearchResultColumnLayoutParams = SearchResultColumnLayoutParams.layoutParameters(kind = layoutKind),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var height by rememberSaveable { mutableIntStateOf(0) }
    val bringIntoViewRequesters = remember { mutableStateMapOf<Int, BringIntoViewRequester>() }
    val wynimeMotionScheme = LocalWynimeMotionScheme.current

    val itemsState = rememberUpdatedState(items)

    SearchResultLazyVerticalGrid(
        items,
        error = {
            LoadErrorCard(
                error = it,
                onRetry = { items.retry() },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier
            .focusGroup()
            .onSizeChanged { height = it.height }
            .keyboardDirectionToSelectItem(
                selectedItemIndex = selectedItemIndex,
                itemCount = { items.itemCount },
            ) {
                state.animateScrollToItem(it)
                onSelect(it)
            }
            .keyboardPageToScroll({ height.toFloat() }) {
                state.animateScrollBy(it)
            },
        cells = layoutParams.grid.gridCells,
        state = state,
        horizontalArrangement = layoutParams.grid.horizontalArrangement,
        verticalArrangement = layoutParams.grid.verticalArrangement,
        contentPadding = contentPadding,
    ) {
        headers()

        item(span = { GridItemSpan(maxLineSpan) }) {
            val scope = remember(this, itemsState, wynimeMotionScheme) {
                SearchResultColumnScopeImpl(itemsState, wynimeMotionScheme)
            }

            scope.summary()
        }

        items(
            count = items.itemCount,
            key = { index ->
                val item = items.peek(index)
                if (item == null) {
                    "search-result-placeholder-$index"
                } else {
                    "search-result-$index-${item.subjectId}"
                }
            },
            contentType = items.itemContentType { 1 },
        ) { index ->
            val info = items[index]

            AnimatedContent(
                layoutParams.kind,
                transitionSpec = wynimeMotionScheme.animatedContent.topLevel,
            ) { targetKind ->
                var nsfwMaskState: NsfwMode by rememberSaveable(info?.title) {
                    mutableStateOf(info?.nsfwMode ?: NsfwMode.DISPLAY)
                }
                NsfwMask(
                    mode = nsfwMaskState,
                    onTemporarilyDisplay = { nsfwMaskState = NsfwMode.DISPLAY },
                    shape = layoutParams.grid.cardShape,
                ) {
                    when (targetKind) {
                        SearchResultLayoutKind.COVER -> {
                            SubjectCoverCard(
                                info?.title,
                                info?.imageUrl,
                                isPlaceholder = info == null,
                                onClick = { onSelect(index) },
                                Modifier.animateItem(
                                    wynimeMotionScheme.feedItemFadeInSpec,
                                    wynimeMotionScheme.feedItemPlacementSpec,
                                    wynimeMotionScheme.feedItemFadeOutSpec,
                                ),
                                shape = layoutParams.grid.cardShape,
                            )
                        }

                        SearchResultLayoutKind.PREVIEW -> {
                            if (info != null && !info.hide) {
                                val requester = remember { BringIntoViewRequester() }

                                DisposableEffect(requester) {
                                    bringIntoViewRequesters[info.subjectId] = requester
                                    onDispose {
                                        bringIntoViewRequesters.remove(info.subjectId)
                                    }
                                }

                                SearchResultItem(
                                    info = info,
                                    selected = highlightSelected && index == selectedItemIndex(),
                                    shape = layoutParams.previewItem.shape,
                                    onClick = { onSelect(index) },
                                    onPlay = onPlay,
                                    Modifier
                                        .animateItem(
                                            wynimeMotionScheme.feedItemFadeInSpec,
                                            wynimeMotionScheme.feedItemPlacementSpec,
                                            wynimeMotionScheme.feedItemFadeOutSpec,
                                        )
                                        .bringIntoViewRequester(requester),
                                    imageModifier = Modifier,
                                )
                            } else {
                                Box(Modifier.size(Dp.Hairline))
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow(selectedItemIndex)
            .collectLatest {
                bringIntoViewRequesters[items.itemSnapshotList.getOrNull(it)?.subjectId]?.bringIntoView()
            }
    }
}

internal data class SearchResultColumnLayoutParams(
    val kind: SearchResultLayoutKind,
    val grid: SubjectGridLayoutParams,
    val previewItem: SubjectItemLayoutParameters,
) {
    companion object {
        @Composable
        fun layoutParameters(
            kind: SearchResultLayoutKind,
            windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()
        ): SearchResultColumnLayoutParams {
            val subjectItem = SubjectItemLayoutParameters.calculate(windowAdaptiveInfo.windowSizeClass)

            return SearchResultColumnLayoutParams(
                kind = kind,
                grid = when (kind) {
                    SearchResultLayoutKind.COVER -> SubjectGridDefaults.coverLayoutParameters(windowAdaptiveInfo)
                    SearchResultLayoutKind.PREVIEW -> {
                        SubjectGridLayoutParams(
                            gridCells = GridCells.Adaptive(360.dp),
                            horizontalArrangement = Arrangement.spacedBy(windowAdaptiveInfo.windowSizeClass.paneHorizontalPadding),
                            verticalArrangement = Arrangement.Top,
                            cardShape = subjectItem.shape,
                        )
                    }
                },
                subjectItem,
            )
        }
    }
}

enum class SearchResultLayoutKind {
    COVER,
    PREVIEW, ;

    companion object {
        fun next(kind: SearchResultLayoutKind): SearchResultLayoutKind {
            return entries[(kind.ordinal + 1) % entries.size]
        }
    }
}

@Composable
private fun SearchResultItem(
    info: SubjectPreviewItemInfo,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onPlay: (SubjectPreviewItemInfo) -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
) {
    SubjectPreviewItem(
        selected = selected,
        onClick = onClick,
        onPlay = { onPlay(info) },
        info = info,
        modifier
            .fillMaxWidth()
            .padding(vertical = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding / 2),
        image = {
            Box(imageModifier) {
                SubjectItemDefaults.Image(
                    info.imageUrl,
                    Modifier.clip(shape),
                )
            }
        },
        title = { maxLines ->
            Text(
                info.title,
                maxLines = maxLines,
            )
        },
    )
}

@Suppress("FunctionName")
private fun LazyGridItemScope.SearchResultColumnScopeImpl(
    itemsState: State<LazyPagingItems<SubjectPreviewItemInfo>>,
    wynimeMotionScheme: WynimeMotionScheme,
): SearchResultColumnScope = object : SearchResultColumnScope {
    @Composable
    override fun SearchSummary(
        layoutKind: SearchResultLayoutKind,
        currentSort: SearchSort,
        onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
        onSortChange: (SearchSort) -> Unit,
        modifier: Modifier
    ) {
        val modifier1 = modifier
        val noResultsText = stringResource(Lang.foundation_load_error_no_results)
        when {
            itemsState.value.isFinishedAndEmpty -> {
                ListItem(
                    headlineContent = { Text(noResultsText) },
                    modifier = modifier1,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                )
            }

            itemsState.value.hasFirstPage -> {
                Surface(modifier1, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.aligned(Alignment.CenterVertically),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Lang.exploration_search_results_shown, itemsState.value.itemCount),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(
                            Modifier.weight(1f).align(Alignment.Bottom),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.aligned(Alignment.End),
                        ) {
                            LayoutKindButton(
                                layoutKind,
                                onLayoutKindChange,
                            )
                            SortButton(
                                currentSort,
                                onSortChange,
                            )
                        }
                    }
                }
            }

            else -> {
                Spacer(modifier1.height(Dp.Hairline))
            }
        }
    }
}

interface SearchResultColumnScope {
    @Composable
    fun SearchSummary(
        layoutKind: SearchResultLayoutKind,
        currentSort: SearchSort,
        onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
        onSortChange: (SearchSort) -> Unit,
        modifier: Modifier = Modifier,
    )
}

@Composable
private fun LayoutKindButton(
    layoutKind: SearchResultLayoutKind,
    onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            onLayoutKindChange(SearchResultLayoutKind.next(layoutKind))
        },
        modifier,
    ) {
        Icon(
            when (layoutKind) {
                SearchResultLayoutKind.COVER -> Icons.Outlined.BackgroundDotLarge
                SearchResultLayoutKind.PREVIEW -> Icons.Outlined.GalleryThumbnail
            },
            layoutKind.name,
        )
    }
}

@Composable
private fun SortButton(
    currentSort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortLabels = rememberSearchSortLabels()
    Box(
        modifier, contentAlignment = Alignment.BottomEnd,
    ) {
        var showDropdown by rememberSaveable {
            mutableStateOf(false)
        }
        IconTextButton(
            onClick = { showDropdown = true },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.Sort, null)
            },
        ) {
            Text(getSortText(currentSort, sortLabels), softWrap = false)
        }
        DropdownMenu(showDropdown, { showDropdown = false }) {
            for (sort in SearchSort.entries) {
                SelectableDropdownMenuItem(
                    selected = sort == currentSort,
                    text = {
                        Text(
                            getSortText(sort, sortLabels),
                            softWrap = false,
                        )
                    },
                    onClick = {
                        showDropdown = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Immutable
private data class SearchSortLabels(
    val match: String,
    val collection: String,
    val rank: String,
    val date: String,
)

@Composable
private fun rememberSearchSortLabels(): SearchSortLabels = SearchSortLabels(
    match = stringResource(Lang.exploration_search_sort_match),
    collection = stringResource(Lang.exploration_search_sort_collection),
    rank = stringResource(Lang.exploration_search_sort_rank),
    date = stringResource(Lang.exploration_search_sort_date),
)

private fun getSortText(currentSort: SearchSort, labels: SearchSortLabels): String = when (currentSort) {
    SearchSort.MATCH -> labels.match
    SearchSort.COLLECTION -> labels.collection
    SearchSort.RANK -> labels.rank
    SearchSort.DATE -> labels.date
}
