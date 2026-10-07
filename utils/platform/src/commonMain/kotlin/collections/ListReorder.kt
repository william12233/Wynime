package com.wynime.utils.platform.collections

import androidx.collection.mutableIntListOf
import androidx.collection.mutableObjectIntMapOf

fun <T, Key : Comparable<Key>> List<T>.partiallyReorderBy(
    getKey: (T) -> Key,
    newPartialOrder: List<Key>
): List<T> {
    if (newPartialOrder.isEmpty() || size <= 1) return this.toList()

    val elements = this
    val keys = elements.map(getKey)

    val keyToPartialIndex = mutableObjectIntMapOf<Key>().apply {
        keys.forEachIndexed { index, key -> put(key, index) }
    }

    val partialOrderIndices = mutableIntListOf().apply {
        newPartialOrder.forEach { pKey ->
            val index = elements.indexOfFirst { getKey(it) == pKey }
            if (index != -1) add(index)
        }
    }

    val adjacency = MutableList(size) { mutableIntListOf() }
    val inDegree = IntArray(size)

    for (i in 0 until partialOrderIndices.size - 1) {
        val from = partialOrderIndices[i]
        val to = partialOrderIndices[i + 1]
        adjacency[from].add(to)
    }

    fun createsCycle(u: Int, v: Int): Boolean {

        adjacency[u].add(v)
        val visited = IntArray(size) { 0 }

        fun dfs(n: Int): Boolean {
            if (visited[n] == 1) return true
            if (visited[n] == 2) return false
            visited[n] = 1
            adjacency[n].forEach { w ->
                if (dfs(w)) return true
            }
            visited[n] = 2
            return false
        }

        val hasCycle = (0 until size).any { visited[it] == 0 && dfs(it) }
        if (hasCycle) {

            adjacency[u].remove(v)
        }
        return hasCycle
    }

    fun partialContradicts(from: Int, to: Int): Boolean {
        val fromPartialIndex = keyToPartialIndex.getOrElse(keys[from]) { -1 }
        val toPartialIndex = keyToPartialIndex.getOrElse(keys[to]) { -1 }

        if (fromPartialIndex != -1 && toPartialIndex != -1) {

            if (fromPartialIndex > toPartialIndex) {

                return true
            }
        }

        return false
    }

    for (i in 0 until size - 1) {
        val u = i
        val v = i + 1

        if (!partialContradicts(u, v)) {

            @Suppress("ControlFlowWithEmptyBody")
            if (createsCycle(u, v)) {

            } else {

            }
        }
    }

    inDegree.fill(0)
    for (u in adjacency.indices) {
        adjacency[u].forEach { v ->
            inDegree[v]++
        }
    }

    val zeroInDegree = IntPriorityQueue()
    for (i in 0 until size) {
        if (inDegree[i] == 0) zeroInDegree.add(i)
    }

    val resultIndices = mutableListOf<Int>()
    while (zeroInDegree.isNotEmpty()) {

        val u = zeroInDegree.removeFirst()
        resultIndices.add(u)
        adjacency[u].forEach { w ->
            inDegree[w]--
            if (inDegree[w] == 0) {
                zeroInDegree.add(w)
            }
        }
    }

    if (resultIndices.size < size) return this.toList()

    return resultIndices.map { elements[it] }
}

fun <T : Comparable<T>> List<T>.partiallyReorderBy(
    newPartialOrder: List<T>
) = partiallyReorderBy({ it }, newPartialOrder)
