package com.wynime.app.data.models.schedule

import kotlinx.serialization.Serializable
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.utils.platform.collections.tupleOf
import kotlin.time.Instant

data class AnimeScheduleInfo(
    val seasonId: AnimeSeasonId,
    val list: List<OnAirAnimeInfo>
)

fun AnimeScheduleInfo.findRecurrence(subjectId: Int): AnimeRecurrence? {
    return list.find { it.bangumiId == subjectId }?.recurrence
}

data class OnAirAnimeInfo(
    val bangumiId: Int,
    val name: String,
    val aliases: List<String>,
    val begin: Instant? = null,
    val recurrence: AnimeRecurrence? = null,
    val end: Instant? = null,
    val mikanId: Int?,
)

typealias AnimeRecurrence = SubjectRecurrence

@Serializable
enum class AnimeSeason(val quarterNumber: Int, val monthRange: Set<Int>) {
    WINTER(1, setOf(12, 1, 2)),
    SPRING(2, setOf(3, 4, 5)),
    SUMMER(3, setOf(6, 7, 8)),
    AUTUMN(4, setOf(9, 10, 11)),
    ;

    companion object {
        fun fromQuarterNumber(number: Int) = entries.find { it.quarterNumber == number }
    }
}

@Serializable
data class AnimeSeasonId(
    val year: Int,
    val season: AnimeSeason,
) : Comparable<AnimeSeasonId> {

    val id: String = "${year}q${season.quarterNumber}"

    companion object {
        private val COMPARATOR = compareBy<AnimeSeasonId> { it.year }
            .thenBy { it.season }

        fun parseOrNull(string: String): AnimeSeasonId? {
            if (!string.contains("q")) {
                return null
            }
            return AnimeSeasonId(
                year = string.substringBefore('q').toIntOrNull() ?: return null,
                season = AnimeSeason.fromQuarterNumber(
                    string.substringAfter('q').toIntOrNull() ?: return null,
                ) ?: return null,
            )
        }

        private val monthLookUpTable = arrayOfNulls<AnimeSeason>(13).apply {
            for (season in AnimeSeason.entries) {
                for (month in season.monthRange) {
                    this[month] = season
                }
            }
        }

        fun fromDate(year: Int, month: Int): AnimeSeasonId {
            if (month == 12) {
                return AnimeSeasonId(year + 1, AnimeSeason.WINTER)
            }
            require(month in 1..12) { "Invalid month: $month" }
            return AnimeSeasonId(year, monthLookUpTable[month]!!)

        }
    }

    override fun compareTo(other: AnimeSeasonId): Int = COMPARATOR.compare(this, other)
}

val AnimeSeasonId.yearMonths
    get() = when (season) {

        AnimeSeason.WINTER -> tupleOf(year - 1 to 12, year to 1, year to 2)
        AnimeSeason.SPRING -> tupleOf(year to 3, year to 4, year to 5)
        AnimeSeason.SUMMER -> tupleOf(year to 6, year to 7, year to 8)
        AnimeSeason.AUTUMN -> tupleOf(year to 9, year to 10, year to 11)
    }
