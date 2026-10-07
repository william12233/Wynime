package com.wynime.app.data.repository

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import com.wynime.utils.coroutines.flows.shareTransparentlyIn
import com.wynime.utils.logging.thisLogger
import kotlin.coroutines.CoroutineContext

abstract class Repository(
    protected val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) {
    protected val logger = thisLogger()

    private val sharingScope = CoroutineScope(defaultDispatcher)

    protected fun <T> Flow<T>.cachedWithTransparentException(): Flow<T> {

        return shareTransparentlyIn(sharingScope, started = SharingStarted.WhileSubscribed(), replay = 1)
    }

    companion object {
        val defaultPagingConfig = PagingConfig(
            pageSize = 30,
        )
    }
}

internal fun <T : Any> List<T>?.toPage(pageNumber: Int): PagingSource.LoadResult.Page<Int, T> {
    val items = this
    return PagingSource.LoadResult.Page(
        data = items ?: emptyList(),
        prevKey = if (pageNumber > 0) pageNumber - 1 else null,
        nextKey = if (!items.isNullOrEmpty()) pageNumber + 1 else null,
    )
}
