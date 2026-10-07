package com.wynime.datasources.api.paging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

interface PagedSource<out T> : SizedSource<T> {

    override val results: Flow<T>

    override val finished: StateFlow<Boolean>

    val currentPage: StateFlow<Int>

    override val totalSize: StateFlow<Int?>

    suspend fun nextPageOrNull(): List<T>?

    fun skipToPage(page: Int)

    fun backToPrevious()
}

inline fun <T, R> PagedSource<T>.map(crossinline transform: suspend (T) -> R): PagedSource<R> {
    return convert(
        flowOperator = { flow ->
            flow.map { transform(it) }
        },
        listOperator = { list ->
            list.map { transform(it) }
        },
    )
}

inline fun <T, R> PagedSource<T>.mapNotNull(crossinline transform: suspend (T) -> R?): PagedSource<R & Any> {
    return convert(
        flowOperator = { flow ->
            flow.mapNotNull { transform(it) }
        },
        listOperator = { list ->
            list.mapNotNull { transform(it) }
        },
    )
}

inline fun <T, R> PagedSource<T>.flatMapConcat(crossinline transform: suspend (T) -> List<R>): PagedSource<R> {
    return convert(
        flowOperator = { flow ->
            flow.flatMapConcat { transform(it).asFlow() }
        },
        listOperator = { list ->
            list.flatMap { transform(it) }
        },
    )
}

@PublishedApi
internal inline fun <T, R> PagedSource<T>.convert(
    crossinline flowOperator: (Flow<T>) -> Flow<R>,
    crossinline listOperator: suspend (List<T>) -> List<R>,
): PagedSource<R> {
    val self = this
    return object : PagedSource<R> {
        override val results: Flow<R> = flowOperator(self.results)
        override val finished: StateFlow<Boolean> get() = self.finished
        override val currentPage: StateFlow<Int> get() = self.currentPage
        override val totalSize: StateFlow<Int?> get() = self.totalSize

        override suspend fun nextPageOrNull(): List<R>? {
            val nextPageOrNull = self.nextPageOrNull()
            return try {
                nextPageOrNull?.let {
                    listOperator(it)
                }
            } catch (e: CancellationException) {
                self.backToPrevious()
                throw e
            }
        }

        override fun skipToPage(page: Int) {
            self.skipToPage(page)
        }

        override fun backToPrevious() {
            self.backToPrevious()
        }
    }
}
