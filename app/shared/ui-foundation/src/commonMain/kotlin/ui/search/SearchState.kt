package com.wynime.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.launchAsLazyPagingItemsIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.platform.annotations.TestOnly

@Stable
abstract class SearchState<T : Any> {

    abstract val pagerFlow: StateFlow<Flow<PagingData<T>>?>

    abstract fun startSearch()

    abstract fun clear()
}

@Composable
fun <T : Any> SearchState<T>.collectItemsWithLifecycle(): LazyPagingItems<T> {
    val pagerFlow = pagerFlow
    val pager by pagerFlow.collectAsStateWithLifecycle(
        initialValue = pagerFlow.value,
    )
    @Suppress("UNCHECKED_CAST")
    return (pager ?: emptyPager as Flow<PagingData<T>>).collectAsLazyPagingItemsWithLifecycle()
}

fun <T : Any> SearchState<T>.launchAsItemsIn(
    scope: CoroutineScope,
): LazyPagingItems<T> = pagerFlow.flatMapLatest { pager ->
    @Suppress("UNCHECKED_CAST")
    pager ?: emptyPager as Flow<PagingData<T>>
}.launchAsLazyPagingItemsIn(scope)

@Composable
fun <T : Any> SearchState<T>.collectHasQueryAsState(): State<Boolean> {
    val value by pagerFlow.collectAsStateWithLifecycle(
        initialValue = pagerFlow.value,
    )

    return remember {
        derivedStateOf {
            value != null
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Stable
private val emptyPager: Flow<PagingData<Any>> = flowOf(
    PagingData.from(
        emptyList(),
        sourceLoadStates = LoadStates(
            LoadState.NotLoading(endOfPaginationReached = true),
            LoadState.NotLoading(endOfPaginationReached = true),
            LoadState.NotLoading(endOfPaginationReached = true),
        ),
    ),
).cachedIn(GlobalScope)

@Stable
class PagingSearchState<T : Any>(

    private val createPager: (scope: CoroutineScope) -> Flow<PagingData<T>>,

    private val backgroundScope: CoroutineScope,
) : SearchState<T>() {
    private data class State<T : Any>(
        val scope: CoroutineScope,
        val pager: Flow<PagingData<T>>,
    )

    private val currentPager: MutableStateFlow<PagingSearchState.State<T>?> = MutableStateFlow(null)
    override val pagerFlow: StateFlow<Flow<PagingData<T>>?> = currentPager.map { it?.pager }
        .stateIn(backgroundScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

    override fun startSearch() {
        clear()

        val scope = backgroundScope.childScope()
        currentPager.value = State(
            scope,
            createPager(scope),
        )
    }

    override fun clear() {
        val prev = currentPager.value
        prev?.scope?.cancel()
        currentPager.value = null
    }
}

@TestOnly
class TestSearchState<T : Any>(
    override val pagerFlow: MutableStateFlow<Flow<PagingData<T>>?>,
) : SearchState<T>() {
    override fun startSearch() {
    }

    override fun clear() {
    }
}
