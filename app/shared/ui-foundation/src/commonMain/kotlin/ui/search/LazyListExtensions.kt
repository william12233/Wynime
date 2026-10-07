package com.wynime.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.layout.minimumHairlineSize
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_search_no_more_items
import org.jetbrains.compose.resources.stringResource

fun <T : Any> LazyListScope.loadErrorItem(
    items: LazyPagingItems<T>
) {
    if (items.loadState.hasError) {
        item {
            Box(Modifier.fillMaxWidth().minimumHairlineSize(), contentAlignment = Alignment.TopCenter) {
                val problem by items.rememberLoadErrorState()
                LoadErrorCard(
                    problem,
                    onRetry = {
                        items.refresh()
                    },
                )
            }
        }
    }
}

fun <T : Any> LazyListScope.noMoreItemsItem(
    items: LazyPagingItems<T>
) {
    when {
        items.loadState.append.endOfPaginationReached -> {
            item {
                val motionScheme = LocalWynimeMotionScheme.current
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).minimumHairlineSize().animateItem(
                        fadeInSpec = motionScheme.feedItemFadeInSpec,
                        placementSpec = motionScheme.feedItemPlacementSpec,
                        fadeOutSpec = motionScheme.feedItemFadeOutSpec,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Lang.foundation_search_no_more_items),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

fun <T : Any> LazyListScope.loadingIndicatorItem(
    items: LazyPagingItems<T>,
    ignoreRefresh: Boolean = false,
) {
    when {
        if (ignoreRefresh) items.isLoadingNextPage else (items.isLoadingFirstPageOrRefreshing || items.isLoadingFirstOrNextPage) -> {
            item {
                val motionScheme = LocalWynimeMotionScheme.current
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).animateItem(
                        fadeInSpec = motionScheme.feedItemFadeInSpec,
                        placementSpec = motionScheme.feedItemPlacementSpec,
                        fadeOutSpec = motionScheme.feedItemFadeOutSpec,
                    ),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

fun <T : Any> LazyListScope.pagingFooterStateItem(
    items: LazyPagingItems<T>,
    ignoreRefresh: Boolean = false,
) {
    noMoreItemsItem(items)
    loadingIndicatorItem(items, ignoreRefresh)
}
