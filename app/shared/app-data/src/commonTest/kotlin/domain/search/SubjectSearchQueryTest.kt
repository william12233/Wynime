package com.wynime.app.domain.search

import com.wynime.app.data.models.schedule.AnimeSeason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubjectSearchQueryTest {
    @Test
    fun `season cannot be set without year`() {
        assertFailsWith<IllegalArgumentException> {
            SubjectSearchQuery("", season = AnimeSeason.SPRING)
        }
    }

    @Test
    fun `clearing year also clears dependent season`() {
        val query = SubjectSearchQuery("", year = 2024, season = AnimeSeason.SPRING)

        val cleared = query.withYearFilter(null)

        assertNull(cleared.year)
        assertNull(cleared.season)
    }

    @Test
    fun `switching to another year keeps the selected season`() {
        val query = SubjectSearchQuery("", year = 2024, season = AnimeSeason.SPRING)

        val switched = query.withYearFilter(2025)

        assertEquals(2025, switched.year)
        assertEquals(AnimeSeason.SPRING, switched.season)
    }
}
