package com.wynime.app.data.models.subject

import com.wynime.app.domain.episode.EpisodeAirTime
import com.wynime.app.domain.episode.EpisodeCompletionContext.resolveEpisodeAirTime
import com.wynime.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SubjectRecurrenceTest {
    private fun exact(iso: String) = EpisodeAirTime(Instant.parse(iso), exact = true)
    private fun dayStart(iso: String) = EpisodeAirTime(Instant.parse(iso), exact = false)

    @Test
    fun `first episode on the start date returns startTime`() {

        val sut = SubjectRecurrence(startTime = Instant.parse("2025-01-03T00:00:00Z"), interval = 7.days)
        assertEquals(exact("2025-01-03T00:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 1, 3), sut))
    }

    @Test
    fun `n-th episode returns startTime plus n intervals`() {

        val sut = SubjectRecurrence(Instant.parse("2025-01-01T12:00:00Z"), 7.days)
        assertEquals(exact("2025-02-05T12:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 2, 5), sut))
    }

    @Test
    fun `date before the premiere by more than 30h falls back to day precision`() {

        val sut = SubjectRecurrence(Instant.parse("2025-03-10T00:00:00Z"), 7.days)
        assertEquals(dayStart("2025-03-08T15:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 3, 9), sut))
    }

    @Test
    fun `day after a slot is not matched to that slot`() {

        val sut = SubjectRecurrence(Instant.parse("2024-06-01T00:00:00Z"), 14.days)
        assertEquals(dayStart("2024-06-01T15:00:00Z"), resolveEpisodeAirTime(PackedDate(2024, 6, 2), sut))
    }

    @Test
    fun `date far from any slot falls back instead of returning null`() {

        val sut = SubjectRecurrence(Instant.parse("2025-02-01T00:00:00Z"), 7.days)
        assertEquals(dayStart("2025-02-02T15:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 2, 3), sut))
    }

    @Test
    fun `zero or negative interval falls back instead of returning null`() {

        val start = Instant.parse("2025-05-01T00:00:00Z")
        val anyDate = PackedDate(2025, 5, 1)
        assertEquals(
            dayStart("2025-04-30T15:00:00Z"),
            resolveEpisodeAirTime(anyDate, SubjectRecurrence(start, Duration.ZERO)),
            "zero interval",
        )
        assertEquals(
            dayStart("2025-04-30T15:00:00Z"),
            resolveEpisodeAirTime(anyDate, SubjectRecurrence(start, (-7).days)),
            "negative interval",
        )
    }
}
