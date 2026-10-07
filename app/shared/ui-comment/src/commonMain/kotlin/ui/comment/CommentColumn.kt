package com.wynime.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import com.wynime.app.ui.foundation.interaction.nestedScrollWorkaround
import com.wynime.app.ui.foundation.layout.ConnectedScrollState
import com.wynime.app.ui.foundation.theme.stronglyWeaken
import com.wynime.app.ui.foundation.thenNotNull
import com.wynime.app.ui.foundation.widgets.PullToRefreshBox
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.ui.search.SearchResultLazyVerticalGrid
import com.wynime.app.ui.search.isFinishedAndEmpty
import com.wynime.app.ui.search.isLoadingFirstPageOrRefreshing
import com.wynime.app.ui.search.isLoadingNextPage

@Composable
fun CommentOverlayCleanupEffect(state: CommentState, items: LazyPagingItems<UIComment>) {
    key(state) {
        CommentOverlayCleanupEffect(items, state::clearStaleOverlays)
    }
}

@Composable
fun CommentOverlayCleanupEffect(items: LazyPagingItems<UIComment>, onRefreshCompleted: () -> Unit) {
    val currentOnRefreshCompleted by rememberUpdatedState(onRefreshCompleted)
    LaunchedEffect(items) {
        snapshotFlow { items.loadState.refresh }
            .distinctUntilChanged()
            .collect { refresh ->
                if (refresh is LoadState.NotLoading) {
                    currentOnRefreshCompleted()
                }
            }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentColumn(
    items: LazyPagingItems<UIComment>,
    modifier: Modifier = Modifier,
    hasDividerLine: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    connectedScrollState: ConnectedScrollState? = null,
    state: LazyGridState = rememberLazyGridState(),
    pullToRefreshEnabled: Boolean = true,
    commentItem: @Composable LazyGridItemScope.(index: Int, item: UIComment) -> Unit
) {
    val emptyContentModifier = Modifier
        .thenNotNull(
            connectedScrollState?.let {
                Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
            },
        )
    val listContentModifier = Modifier
        .thenNotNull(
            connectedScrollState?.let {
                Modifier.nestedScroll(connectedScrollState.nestedScrollConnection)
                    .nestedScrollWorkaround(state, connectedScrollState)
            },
        )

    PullToRefreshBox(
        isRefreshing = items.isLoadingFirstPageOrRefreshing,
        onRefresh = { items.refresh() },
        modifier = modifier,
        enabled = pullToRefreshEnabled,
        touchOnly = true,
        contentAlignment = Alignment.TopCenter,
    ) {
        if (items.isFinishedAndEmpty) {
            Box(
                modifier = emptyContentModifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                CommentDefaults.EmptyPlaceholder()
            }
            return@PullToRefreshBox
        }

        SearchResultLazyVerticalGrid(
            items,
            error = {
                LoadErrorCard(
                    error = it,
                    onRetry = { items.retry() },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            modifier = listContentModifier,
            contentPadding = contentPadding,
            cells = GridCells.Fixed(1),
            showLoadingIndicatorInFirstPage = false,
            state = state,
        ) {
            item("spacer header") { Spacer(Modifier.height(1.dp)) }

            items(
                items.itemCount,
                key = items.itemKey { "CommentColumn-" + it.stableId },
                contentType = items.itemContentType(),
            ) { index ->
                Column {
                    val item = items[index] ?: return@items
                    commentItem(index, item)

                    if (hasDividerLine && index != items.itemCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = DividerDefaults.color.stronglyWeaken(),
                        )
                    }
                }
            }

            if (items.isLoadingNextPage) {
                item("dummy loader") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}
