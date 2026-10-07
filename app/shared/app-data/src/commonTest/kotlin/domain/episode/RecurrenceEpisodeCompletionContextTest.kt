package com.wynime.app.domain.episode

import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.app.domain.episode.EpisodeCompletionContext.mapAirDate
import com.wynime.app.domain.episode.EpisodeCompletionContext.resolveEpisodeAirTime
import com.wynime.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class RecurrenceEpisodeCompletionContextTest {

    private val weekly = SubjectRecurrence(
        startTime = Instant.parse("2024-11-14T10:00:00+09:00"),
        interval = 7.days,
    )

    private fun exact(iso: String) = EpisodeAirTime(Instant.parse(iso), exact = true)
    private fun dayStart(iso: String) = EpisodeAirTime(Instant.parse(iso), exact = false)

    @Test
    fun `null recurrence maps to the air date at 00 00 JST`() {
        val airDate = PackedDate(2024, 11, 20)
        assertEquals(dayStart("2024-11-20T00:00:00+09:00"), resolveEpisodeAirTime(airDate, null))
        assertEquals(Instant.parse("2024-11-20T00:00:00+09:00"), null.mapAirDate(airDate))
    }

    @Test
    fun `invalid air date maps to null`() {
        assertNull(resolveEpisodeAirTime(PackedDate.Invalid, weekly))
        assertNull(resolveEpisodeAirTime(PackedDate.Invalid, null))
        assertNull(resolveEpisodeAirTime(PackedDate.parseFromDate("2099"), weekly), "year only")
        assertNull(resolveEpisodeAirTime(PackedDate.parseFromDate(""), weekly), "blank")
        assertNull(resolveEpisodeAirTime(PackedDate(2023, 2, 31), weekly), "structurally valid but not a real date")
        assertNull(weekly.mapAirDate(PackedDate.Invalid))
        assertNull(null.mapAirDate(PackedDate.Invalid))
    }

    @Test
    fun `slot on the same JST day is exact`() {

        assertEquals(exact("2024-11-21T10:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 21), weekly))
        assertEquals(Instant.parse("2024-11-21T10:00:00+09:00"), weekly.mapAirDate(PackedDate(2024, 11, 21)))
    }

    @Test
    fun `slot more than 30h after the listed day falls back to day precision`() {

        assertEquals(dayStart("2024-11-20T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 20), weekly))
        assertEquals(dayStart("2024-11-29T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 29), weekly))
    }

    @Test
    fun `no previous-slot branch - the day after a slot falls back`() {

        assertEquals(dayStart("2024-11-22T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 22), weekly))
        assertEquals(dayStart("2024-11-15T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 15), weekly))
    }

    @Test
    fun `late-night slot listed on the previous day is exact`() {

        val lateNight = SubjectRecurrence(Instant.parse("2024-11-14T01:00:00+09:00"), 7.days)
        assertEquals(exact("2024-11-14T01:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 13), lateNight))
        assertEquals(exact("2024-11-14T01:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 14), lateNight))
    }

    @Test
    fun `upper bound is 30h inclusive`() {
        val airDate = PackedDate(2024, 11, 14)
        val dayStartInstant = Instant.parse("2024-11-13T15:00:00Z")

        val at24h = SubjectRecurrence(dayStartInstant + 24.hours, 7.days)
        assertEquals(exact("2024-11-14T15:00:00Z"), resolveEpisodeAirTime(airDate, at24h))

        val at30h = SubjectRecurrence(dayStartInstant + 30.hours, 7.days)
        assertEquals(exact("2024-11-14T21:00:00Z"), resolveEpisodeAirTime(airDate, at30h))

        val over30h = SubjectRecurrence(dayStartInstant + 30.hours + 1.milliseconds, 7.days)
        assertEquals(dayStart("2024-11-13T15:00:00Z"), resolveEpisodeAirTime(airDate, over30h))
    }

    @Test
    fun `air date before the premiere resolves to startTime only within the bound`() {
        val premiere = SubjectRecurrence(Instant.parse("2024-11-14T05:00:00+09:00"), 7.days)

        assertEquals(exact("2024-11-14T05:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 13), premiere))

        assertEquals(dayStart("2024-10-01T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 10, 1), premiere))

        assertEquals(dayStart("2024-11-13T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 13), weekly))
    }

    @Test
    fun `interval shorter than one day falls back even when startTime matches the day`() {
        val airDate = PackedDate(2024, 11, 14)
        val start = Instant.parse("2024-11-14T00:00:00+09:00")
        for (interval in listOf(Duration.ZERO, 12.hours, 24.hours - 1.milliseconds, (-7).days)) {
            assertEquals(
                dayStart("2024-11-14T00:00:00+09:00"),
                resolveEpisodeAirTime(airDate, SubjectRecurrence(start, interval)),
                "interval=$interval",
            )
        }
        assertEquals(
            exact("2024-11-14T00:00:00+09:00"),
            resolveEpisodeAirTime(airDate, SubjectRecurrence(start, 24.hours)),
            "exactly one day is allowed",
        )
    }

    @Test
    fun `daily slot resolves to the listed date not the previous day`() {
        val daily = SubjectRecurrence(Instant.parse("2024-11-01T23:00:00+09:00"), 1.days)
        assertEquals(exact("2024-11-05T23:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 5), daily))
    }

    @Test
    fun `every-two-days takes the first slot at or after the listed date`() {

        val everyTwoDays = SubjectRecurrence(Instant.parse("2024-11-01T19:00:00+09:00"), 2.days)
        assertEquals(exact("2024-11-03T19:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 3), everyTwoDays))

        assertEquals(dayStart("2024-11-04T00:00:00+09:00"), resolveEpisodeAirTime(PackedDate(2024, 11, 4), everyTwoDays))
    }

    @Test
    fun `monthly interval of 30 days`() {
        val monthly = SubjectRecurrence(Instant.parse("2025-06-28T16:00:00Z"), 30.days)
        assertEquals(exact("2025-07-28T16:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 7, 29), monthly))
    }
}
