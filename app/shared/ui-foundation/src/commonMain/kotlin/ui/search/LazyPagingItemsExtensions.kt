package com.wynime.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.utils.platform.annotations.TestOnly

@Stable
val LazyPagingItems<*>.isLoadingFirstPage: Boolean
    get() = !loadState.isIdle && !loadState.hasError && itemCount == 0

@Stable
val LazyPagingItems<*>.isLoadingFirstPageOrRefreshing: Boolean
    get() = isLoadingFirstPage || loadState.refresh is LoadState.Loading

@Stable
val LazyPagingItems<*>.isLoadingFirstOrNextPage: Boolean
    get() = isLoadingFirstPage || isLoadingNextPage

@Stable
val LazyPagingItems<*>.isLoadingNextPage: Boolean
    get() = loadState.append is LoadState.Loading

@Stable
val LazyPagingItems<*>.hasFirstPage: Boolean
    get() = itemCount > 0

@Stable
val LazyPagingItems<*>.isFinishedAndEmpty: Boolean
    get() = itemCount == 0 && loadState.isIdle

@TestOnly
@Composable
fun <T : Any> rememberTestLazyPagingItems(list: List<T>): LazyPagingItems<T> {
    return createTestPager(list).collectAsLazyPagingItems()
}

@TestOnly
fun <T : Any> createTestPager(list: List<T>) = MutableStateFlow(PagingData.from(list))
