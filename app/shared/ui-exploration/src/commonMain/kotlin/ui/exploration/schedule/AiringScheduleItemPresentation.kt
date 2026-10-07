package com.wynime.app.ui.exploration.schedule

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.wynime.app.data.models.subject.displayName
import com.wynime.app.data.models.subject.nameOrNameCn
import com.wynime.app.domain.episode.EpisodeWithAiringTime
import com.wynime.app.domain.episode.GetAnimeScheduleFlowUseCase
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.collections.ImmutableEnumMap

@Immutable
data class AiringScheduleItemPresentation(
    val subjectId: Int,
    val subjectTitle: String,

    val subjectOriginalTitle: String = subjectTitle,
    val imageUrl: String,
    val episodeId: Int,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,

    val episodeOriginalName: String? = episodeName,

    val subjectCollectionType: UnifiedCollectionType,
    val dayOfWeek: DayOfWeek,

    val time: LocalTime?,
)

@Immutable
data class AiringSchedule(
    val date: LocalDate,
    val episodes: List<AiringScheduleColumnItem>,
)

@Immutable
data class ScheduleDay(
    val date: LocalDate,
    val kind: Kind,
) {
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek

    enum class Kind {
        LAST_WEEK,
        TODAY,
        THIS_WEEK,
        NEXT_WEEK,
    }

    companion object {
        fun generateForRecentTwoWeeks(
            today: LocalDate,
        ): List<ScheduleDay> {

            return SchedulePageDataHelper.OFFSET_DAYS_RANGE.map { offsetDays ->
                val date = today.plus(DatePeriod(days = offsetDays))
                val thisWeekRange: ClosedRange<LocalDate> = getWeekRange(today)
                ScheduleDay(
                    date = date,
                    kind = when {
                        date == today -> Kind.TODAY
                        date in thisWeekRange -> Kind.THIS_WEEK
                        date > thisWeekRange.endInclusive -> Kind.NEXT_WEEK
                        date < thisWeekRange.start -> Kind.LAST_WEEK
                        else -> error("unreachable")
                    },
                )
            }
        }

        private fun getWeekRange(date: LocalDate): ClosedRange<LocalDate> {
            val dayOfWeek = date.dayOfWeek
            return date.minus(DatePeriod(days = dayOfWeek.ordinal))..date.plus(DatePeriod(days = 6 - dayOfWeek.ordinal))
        }
    }
}

@TestOnly
val TestAiringScheduleItemPresentations
    get() = buildList {
        var id = 0
        repeat(50) { i ->
            repeat(if (i % 8 == 0) 2 else 1) {
                add(
                    AiringScheduleItemPresentation(
                        subjectId = ++id,
                        subjectTitle = "Subject $id",
                        subjectOriginalTitle = "オリジナル $id",
                        imageUrl = "https://example.com/image.jpg",
                        episodeId = id,
                        episodeSort = EpisodeSort(if (i % 3 == 0) 13 else 1),
                        episodeEp = EpisodeSort(1),
                        episodeName = "Episode 1",
                        episodeOriginalName = "エピソード 1",
                        subjectCollectionType = UnifiedCollectionType.entries[i % UnifiedCollectionType.entries.size],
                        dayOfWeek = DayOfWeek.entries[i % DayOfWeek.entries.size],

                        time = if (i % 11 == 10) null else LocalTime(i % 24, 0),
                    ),
                )

            }
        }
    }

private val testPresentationComparator =
    compareBy<AiringScheduleItemPresentation, LocalTime?>(nullsLast()) { it.time }
        .thenBy { it.subjectTitle }

@TestOnly
val TestAiringScheduleItemPresentationData: ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>>
    get() = ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>> { day ->
        TestAiringScheduleItemPresentations.filter { it.dayOfWeek == day }
            .sortedWith(testPresentationComparator)
    }

@TestOnly
val TestSchedulePageData: List<AiringSchedule>
    get() {
        val currentTime = LocalTime(12, 0)
        val list = TestAiringScheduleItemPresentations.filter { it.dayOfWeek == DayOfWeek.MONDAY }
            .sortedWith(testPresentationComparator)

        return ScheduleDay.generateForRecentTwoWeeks(LocalDate(2025, 12, 10)).map {
            AiringSchedule(
                date = it.date,
                SchedulePageDataHelper.toColumnItems(list, addIndicator = true, currentTime),
            )
        }
    }

fun EpisodeWithAiringTime.toPresentation(timeZone: TimeZone): AiringScheduleItemPresentation {

    val dateTime = airingTime.toLocalDateTime(timeZone)

    return AiringScheduleItemPresentation(
        subjectId = subject.subjectId,
        subjectTitle = subject.displayName,
        subjectOriginalTitle = subject.nameOrNameCn,
        imageUrl = subject.imageLarge,
        episodeId = episode.episodeId,
        episodeSort = episode.sort,
        episodeEp = episode.ep,
        episodeName = episode.displayName,
        episodeOriginalName = episode.nameOrNameCn,
        subjectCollectionType = UnifiedCollectionType.NOT_COLLECTED,
        dayOfWeek = dateTime.dayOfWeek,
        time = if (timeKnown) dateTime.time else null,
    )
}

object SchedulePageDataHelper {
    val OFFSET_DAYS_RANGE = GetAnimeScheduleFlowUseCase.OFFSET_DAYS_RANGE

    fun toColumnItems(
        list: List<AiringScheduleItemPresentation>,
        addIndicator: Boolean,
        currentTime: LocalTime,
    ): List<AiringScheduleColumnItem> {
        val (timed, timeUnknown) = list.partition { it.time != null }
        val sortedTimed = timed.sortedBy { it.time }
        val insertionIndex = sortedTimed.indexOfLast { checkNotNull(it.time) <= currentTime }
        return buildList(capacity = list.size + 1) {
            var previousTime: LocalTime? = null
            val handleItem = { itemPresentation: AiringScheduleItemPresentation ->
                val showtime = previousTime != itemPresentation.time
                previousTime = itemPresentation.time
                add(
                    AiringScheduleColumnItem.Data(
                        item = itemPresentation,
                        showtime,
                    ),
                )
            }

            for (itemPresentation in sortedTimed.subList(0, insertionIndex + 1)) {
                handleItem(itemPresentation)
            }
            if (addIndicator) {
                add(
                    AiringScheduleColumnItem.CurrentTimeIndicator(
                        currentTime = currentTime,
                        isPlaceholder = false,
                    ),
                )
            }
            for (itemPresentation in sortedTimed.subList(insertionIndex + 1, sortedTimed.size)) {
                handleItem(itemPresentation)
            }
            timeUnknown.forEachIndexed { index, itemPresentation ->
                add(
                    AiringScheduleColumnItem.Data(
                        item = itemPresentation,
                        showTime = index == 0,
                    ),
                )
            }
        }
    }
}