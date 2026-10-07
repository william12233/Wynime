package com.wynime.app.domain.episode

import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.toLocalDateOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class EpisodeAirTime(
    val instant: Instant,
    val exact: Boolean,
)

object EpisodeCompletionContext {
    private val clock = Clock.System
    private val UTC9 = UtcOffset(hours = 9)

    private val UPPER_BOUND_MILLIS: Long = 30.hours.inWholeMilliseconds
    private val ONE_DAY_MILLIS: Long = 1.days.inWholeMilliseconds

    fun resolveEpisodeAirTime(
        airDate: PackedDate,
        recurrence: SubjectRecurrence?,
    ): EpisodeAirTime? {
        val localDate = airDate.toLocalDateOrNull() ?: return null
        val dayStart = localDate.atTime(LocalTime(0, 0)).toInstant(UTC9)
        if (recurrence == null) return EpisodeAirTime(dayStart, exact = false)

        val intervalMillis = recurrence.interval.inWholeMilliseconds

        if (intervalMillis < ONE_DAY_MILLIS) return EpisodeAirTime(dayStart, exact = false)

        val dayStartMillis = dayStart.toEpochMilliseconds()
        val startMillis = recurrence.startTime.toEpochMilliseconds()
        val diffMillis = dayStartMillis - startMillis

        val k = (-(-diffMillis).floorDiv(intervalMillis)).coerceAtLeast(0)
        val candidateMillis = startMillis + k * intervalMillis

        return if (candidateMillis - dayStartMillis <= UPPER_BOUND_MILLIS) {
            EpisodeAirTime(Instant.fromEpochMilliseconds(candidateMillis), exact = true)
        } else {
            EpisodeAirTime(dayStart, exact = false)
        }
    }

    fun SubjectRecurrence?.mapAirDate(
        airDate: PackedDate,
    ): Instant? = resolveEpisodeAirTime(airDate, this)?.instant

    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?, now: Instant): Boolean {
        val airTime = recurrence.mapAirDate(airDate) ?: return false
        return now >= airTime
    }

    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?): Boolean =
        isKnownCompleted(recurrence, clock.now())

    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?, now: Instant): Boolean {
        val airTime = recurrence.mapAirDate(airDate) ?: return false
        return now < airTime
    }

    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?): Boolean =
        isKnownOnAir(recurrence, clock.now())
}
