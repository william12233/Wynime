package com.wynime.utils.platform.collections

inline fun <T> Collection<T>.mapToIntArray(
    transform: (T) -> Int,
): IntArray {
    val array = IntArray(size)
    var i = 0
    forEach { array[i++] = transform(it) }
    return array
}
