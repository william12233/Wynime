@file:Suppress("ObjectPropertyName", "NOTHING_TO_INLINE")

package com.wynime.utils.coroutines.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@PublishedApi
internal val _flowOfEmptyList = flowOf(emptyList<Any?>())

@Suppress("UNCHECKED_CAST")
inline fun <T> flowOfEmptyList(): Flow<List<T>> = _flowOfEmptyList as Flow<List<T>>

@PublishedApi
internal val sequenceOfEmptyString = sequenceOf("")

inline fun sequenceOfEmptyString(): Sequence<String> = sequenceOfEmptyString

@PublishedApi
internal val _flowOfNull = flowOf(null)

@Suppress("UNCHECKED_CAST")
inline fun <T> flowOfNull(): Flow<T?> = _flowOfNull as Flow<T?>
