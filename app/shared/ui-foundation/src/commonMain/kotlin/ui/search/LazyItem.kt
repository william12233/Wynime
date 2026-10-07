package com.wynime.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class LazyItem<T : Any>(
    private val lazyPagingItems: LazyPagingItems<T>
) {

    val hasItem = lazyPagingItems.itemCount > 0

    val item = lazyPagingItems[0]

    val loadState get() = lazyPagingItems.loadState

    fun retry() {
        lazyPagingItems.retry()
    }

    fun refresh() {
        lazyPagingItems.refresh()
    }
}

@Composable
fun <T : Any> Flow<T>.collectAsLazyItem(
    context: CoroutineContext = EmptyCoroutineContext
): LazyItem<T> {
    val flow = remember(this) {
        this.map {
            PagingData.from(listOf(it))
        }
    }

    val lazyPagingItems = flow.collectAsLazyPagingItems(context)
    return remember(lazyPagingItems) {
        LazyItem(lazyPagingItems)
    }
}

@Composable
fun <T : Any> Flow<T>.collectAsLazyItemWithLifecycle(
    context: CoroutineContext = EmptyCoroutineContext,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): LazyItem<T> {
    val flow = remember(this) {
        this.map {
            PagingData.from(listOf(it))
        }
    }

    val lazyPagingItems = flow.collectAsLazyPagingItemsWithLifecycle(context, lifecycle, minActiveState)
    return remember(lazyPagingItems) {
        LazyItem(lazyPagingItems)
    }
}
