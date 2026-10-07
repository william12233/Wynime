package androidx.paging.compose

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Stable
public class LazyPagingItems<T : Any>
internal constructor(

    private val flow: Flow<PagingData<T>>
) {
    private val mainDispatcher = Dispatchers.Main

    private val pagingDataPresenter =
        object :
            PagingDataPresenter<T>(
                mainContext = mainDispatcher,
                cachedPagingData =
                    if (flow is SharedFlow<PagingData<T>>) flow.replayCache.firstOrNull() else null,
            ) {
            override suspend fun presentPagingDataEvent(
                event: PagingDataEvent<T>,
            ) {
                updateItemSnapshotList()
            }
        }

    var itemSnapshotList by mutableStateOf(pagingDataPresenter.snapshot())
        private set

    val itemCount: Int
        get() = itemSnapshotList.size

    private fun updateItemSnapshotList() {
        itemSnapshotList = pagingDataPresenter.snapshot()
    }

    operator fun get(index: Int): T? {
        pagingDataPresenter[index]
        return itemSnapshotList[index]
    }

    fun peek(index: Int): T? {
        return itemSnapshotList[index]
    }

    fun retry() {
        pagingDataPresenter.retry()
    }

    fun refresh() {
        pagingDataPresenter.refresh()
    }

    public var loadState: CombinedLoadStates by
    mutableStateOf(
        pagingDataPresenter.loadStateFlow.value
            ?: CombinedLoadStates(
                refresh = InitialLoadStates.refresh,
                prepend = InitialLoadStates.prepend,
                append = InitialLoadStates.append,
                source = InitialLoadStates,
            ),
    )
        private set

    fun addLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        pagingDataPresenter.addLoadStateListener(listener)
    }

    fun removeLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        pagingDataPresenter.removeLoadStateListener(listener)
    }

    internal suspend fun collectLoadState() {
        pagingDataPresenter.loadStateFlow.filterNotNull().collect { loadState = it }
    }

    internal suspend fun collectPagingData() {
        flow.collectLatest { pagingDataPresenter.collectFrom(it) }
    }
}

private val IncompleteLoadState = LoadState.NotLoading(false)
private val InitialLoadStates =
    LoadStates(LoadState.Loading, IncompleteLoadState, IncompleteLoadState)

@Composable
public fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(
    context: CoroutineContext = EmptyCoroutineContext
): LazyPagingItems<T> {

    val lazyPagingItems = remember(this) { LazyPagingItems(this) }

    LaunchedEffect(lazyPagingItems) {
        if (context == EmptyCoroutineContext) {
            lazyPagingItems.collectPagingData()
        } else {
            withContext(context) { lazyPagingItems.collectPagingData() }
        }
    }

    LaunchedEffect(lazyPagingItems) {
        if (context == EmptyCoroutineContext) {
            lazyPagingItems.collectLoadState()
        } else {
            withContext(context) { lazyPagingItems.collectLoadState() }
        }
    }

    return lazyPagingItems
}

fun <T : Any> Flow<PagingData<T>>.launchAsLazyPagingItemsIn(
    scope: CoroutineScope,
    context: CoroutineContext = EmptyCoroutineContext
): LazyPagingItems<T> {
    val lazyPagingItems = LazyPagingItems(this)

    scope.launch {
        if (context == EmptyCoroutineContext) {
            lazyPagingItems.collectPagingData()
        } else {
            withContext(context) { lazyPagingItems.collectPagingData() }
        }
    }

    scope.launch {
        if (context == EmptyCoroutineContext) {
            lazyPagingItems.collectLoadState()
        } else {
            withContext(context) { lazyPagingItems.collectLoadState() }
        }
    }

    return lazyPagingItems
}

@Composable
public fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItemsWithLifecycle(
    context: CoroutineContext = EmptyCoroutineContext,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): LazyPagingItems<T> {
    val lazyPagingItems = remember(this) { LazyPagingItems(this) }

    LaunchedEffect(lazyPagingItems) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            if (context == EmptyCoroutineContext) {
                lazyPagingItems.collectPagingData()
            } else {
                withContext(context) { lazyPagingItems.collectPagingData() }
            }
        }
    }

    LaunchedEffect(lazyPagingItems) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            if (context == EmptyCoroutineContext) {
                lazyPagingItems.collectLoadState()
            } else {
                withContext(context) { lazyPagingItems.collectLoadState() }
            }
        }
    }

    return lazyPagingItems
}

@Composable
public fun <T : Any> LazyPagingItems<T>.collectWithLifecycle(
    context: CoroutineContext = EmptyCoroutineContext,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): LazyPagingItems<T> {

    LaunchedEffect(this) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            if (context == EmptyCoroutineContext) {
                collectPagingData()
            } else {
                withContext(context) { collectPagingData() }
            }
        }
    }

    LaunchedEffect(this) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            if (context == EmptyCoroutineContext) {
                collectLoadState()
            } else {
                withContext(context) { collectLoadState() }
            }
        }
    }

    return this
}