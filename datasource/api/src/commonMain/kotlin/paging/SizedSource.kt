package com.wynime.datasources.api.paging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

interface SizedSource<out T> {

    val results: Flow<T>

    val finished: Flow<Boolean>

    val totalSize: Flow<Int?>
}

private object EmptySizedSource : SizedSource<Nothing> {
    override val results = emptyFlow<Nothing>()
    override val finished = flowOf(true)
    override val totalSize = flowOf(0)
}

fun emptySizedSource(): SizedSource<Nothing> = EmptySizedSource

inline fun <T, R> SizedSource<T>.map(crossinline transform: suspend (T) -> R): SizedSource<R> {
    return object : SizedSource<R> {
        override val results = this@map.results.map(transform)
        override val finished = this@map.finished
        override val totalSize = this@map.totalSize
    }
}

inline fun <T, R> SizedSource<T>.mapNotNull(crossinline transform: suspend (T) -> R?): SizedSource<R> {
    val self = this
    return object : SizedSource<R> {
        override val results = self.results.mapNotNull(transform)
        override val finished = self.finished
        override val totalSize = self.totalSize
    }
}

inline fun <T> SizedSource<T>.filter(crossinline predicate: suspend (T) -> Boolean): SizedSource<T> {
    val self = this
    return object : SizedSource<T> {
        override val results = self.results.filter(predicate)
        override val finished = self.finished
        override val totalSize = self.totalSize
    }
}

fun <T> Iterable<SizedSource<T>>.merge(): SizedSource<T> {
    return object : SizedSource<T> {
        override val results: Flow<T> = this@merge.map { it.results }.merge()
        override val finished: Flow<Boolean> = combine(this@merge.map { it.finished }) { values ->
            values.all { it }
        }

        override val totalSize: Flow<Int?> = combine(this@merge.map { it.totalSize }) { values ->
            if (values.any { it == null }) {
                return@combine null
            }
            @Suppress("UNCHECKED_CAST")
            (values as Array<Int>).sum()
        }
    }
}

suspend inline fun SizedSource<*>.awaitFinished() {
    this.finished.filter { it }.first()
}
