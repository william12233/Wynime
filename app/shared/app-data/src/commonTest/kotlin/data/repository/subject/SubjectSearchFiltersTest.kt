package com.wynime.app.data.repository.subject

import com.wynime.app.data.models.schedule.AnimeSeason
import com.wynime.app.domain.search.SubjectSearchQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubjectSearchFiltersTest {
    @Test
    fun `no year means no air date filter`() {
        assertNull(SubjectSearchQuery("").toBangumiAirDates())
    }

    @Test
    fun `year only covers the whole natural year`() {
        assertEquals(
            listOf(">=2024-01-01", "<2025-01-01"),
            SubjectSearchQuery("", year = 2024).toBangumiAirDates(),
        )
    }

    @Test
    fun `winter season starts in previous December and ends in late February`() {

        assertEquals(
            listOf(">=2023-12-01", "<2024-03-01"),
            SubjectSearchQuery("", year = 2024, season = AnimeSeason.WINTER).toBangumiAirDates(),
        )
    }

    @Test
    fun `spring season upper bound is June first`() {
        assertEquals(
            listOf(">=2024-03-01", "<2024-06-01"),
            SubjectSearchQuery("", year = 2024, season = AnimeSeason.SPRING).toBangumiAirDates(),
        )
    }

    @Test
    fun `autumn season upper bound is December first`() {

        assertEquals(
            listOf(">=2024-09-01", "<2024-12-01"),
            SubjectSearchQuery("", year = 2024, season = AnimeSeason.AUTUMN).toBangumiAirDates(),
        )
    }
}
