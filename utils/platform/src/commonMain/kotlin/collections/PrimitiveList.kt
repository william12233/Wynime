package com.wynime.utils.platform.collections

import androidx.collection.IntList
import androidx.collection.MutableIntList

inline fun <T> Collection<T>.mapToIntList(
    transform: (T) -> Int,
): IntList {
    val array = MutableIntList(size)
    forEach { array.add(transform(it)) }
    return array
}

inline fun <V, M : MutableMap<in Int, in V>> IntList.associateWithTo(
    destination: M,
    valueSelector: (Int) -> V
): M {
    this.forEach { element ->
        destination.put(element, valueSelector(element))
    }
    return destination
}

fun IntList.toIntArray(): IntArray {
    val array = IntArray(size)
    forEachIndexed { index, element ->
        array[index] = element
    }
    return array
}