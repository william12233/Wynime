package com.wynime.utils.platform.collections

import kotlin.test.Test
import kotlin.test.assertEquals

class PartiallyReorderByTest {

    @Test
    fun `empty newPartialOrder should leave list unchanged`() {
        val list = mutableListOf("a", "b", "c")
        val newPartialOrder = emptyList<String>()

        assertEquals(listOf("a", "b", "c"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `newPartialOrder reorders subset of elements`() {
        val list = mutableListOf("a", "b", "c", "d", "e")

        val newPartialOrder = listOf("a", "d", "c")

        assertEquals(listOf("a", "b", "d", "c", "e"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `newPartialOrder includes all elements`() {
        val list = mutableListOf("a", "b", "c")

        val newPartialOrder = listOf("c", "a", "b")

        assertEquals(listOf("c", "a", "b"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `newPartialOrder only includes some elements at the end`() {
        val list = mutableListOf("x", "y", "a", "b", "c", "z")

        val newPartialOrder = listOf("c", "a")

        assertEquals(listOf("x", "y", "c", "a", "b", "z"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `no overlapping keys with newPartialOrder`() {
        val list = mutableListOf("m", "n", "o")
        val newPartialOrder = listOf("x", "y", "z")

        assertEquals(listOf("m", "n", "o"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `complex partial order with some keys missing`() {
        val list = mutableListOf("1", "2", "3", "4", "5", "6", "7")

        val newPartialOrder = listOf("2", "6", "3")

        assertEquals(listOf("1", "2", "6", "3", "4", "5", "7"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `partial order has redundant element`() {
        val list = mutableListOf("1", "2", "3", "4", "5", "6", "7")

        val newPartialOrder = listOf("2", "6", "100", "3", "10")

        assertEquals(listOf("1", "2", "6", "3", "4", "5", "7"), list.partiallyReorderBy(newPartialOrder))
    }

    @Test
    fun `custom key extraction`() {
        data class Item(val key: Int, val value: String) : Comparable<Item> {
            override fun compareTo(other: Item): Int = key.compareTo(other.key)
        }

        val list = mutableListOf(
            Item(1, "a"),
            Item(2, "b"),
            Item(3, "c"),
            Item(4, "d"),
        )

        val newPartialOrder = listOf(3, 1)

        assertEquals(listOf(3, 1, 2, 4), list.partiallyReorderBy({ it.key }, newPartialOrder).map { it.key })
    }
}
