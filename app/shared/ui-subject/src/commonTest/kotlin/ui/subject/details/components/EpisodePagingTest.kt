package com.wynime.app.ui.subject.details.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodePagingTest {

    @Test
    fun `capacity must be at least 1`() {
        assertFailsWith<IllegalArgumentException> { EpisodePaging(totalCount = 10, capacity = 0) }
    }

    @Test
    fun `page count is ceil of total over capacity`() {
        assertEquals(0, EpisodePaging(0, 12).pageCount)
        assertEquals(1, EpisodePaging(12, 12).pageCount)
        assertEquals(2, EpisodePaging(13, 12).pageCount)
        assertEquals(11, EpisodePaging(128, 12).pageCount)
    }

    @Test
    fun `is paged only when total exceeds one page`() {
        assertFalse(EpisodePaging(12, 12).isPaged)
        assertFalse(EpisodePaging(5, 12).isPaged)
        assertTrue(EpisodePaging(13, 12).isPaged)
    }

    @Test
    fun `page of item index`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0, p.pageOf(0))
        assertEquals(0, p.pageOf(11))
        assertEquals(1, p.pageOf(12))
        assertEquals(4, p.pageOf(54))
        assertEquals(10, p.pageOf(127))
    }

    @Test
    fun `page of clamps out of range indices`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0, p.pageOf(-5))
        assertEquals(10, p.pageOf(9999))
    }

    @Test
    fun `initial page contains current progress episode`() {
        val p = EpisodePaging(128, 12)
        assertEquals(4, p.initialPage(currentIndex = 54))
        assertEquals(0, p.initialPage(currentIndex = 0))
    }

    @Test
    fun `initial page falls back to first when no current episode`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0, p.initialPage(currentIndex = -1))
    }

    @Test
    fun `item range for full page`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0 until 12, p.itemRange(0))
        assertEquals(48 until 60, p.itemRange(4))
    }

    @Test
    fun `item range truncates last page`() {
        val p = EpisodePaging(128, 12)
        assertEquals(120 until 128, p.itemRange(10))
    }

    @Test
    fun `item range clamps page and handles empty`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0 until 12, p.itemRange(-3))
        assertEquals(120 until 128, p.itemRange(99))
        assertEquals(IntRange.EMPTY, EpisodePaging(0, 12).itemRange(0))
    }
}
