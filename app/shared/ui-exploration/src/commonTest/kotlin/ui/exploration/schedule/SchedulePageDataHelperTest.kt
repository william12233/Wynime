package com.wynime.app.ui.exploration.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import com.wynime.app.data.models.subject.LightEpisodeInfo
import com.wynime.app.data.models.subject.LightSubjectInfo
import com.wynime.app.domain.episode.EpisodeWithAiringTime
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.UTC9
import com.wynime.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SchedulePageDataHelperTest {
    private fun item(
        id: Int,
        time: LocalTime?,
        title: String = "Subject $id",
    ) = AiringScheduleItemPresentation(
        subjectId = id,
        subjectTitle = title,
        imageUrl = "",
        episodeId = id,
        episodeSort = EpisodeSort(1),
        episodeEp = EpisodeSort(1),
        episodeName = null,
        subjectCollectionType = UnifiedCollectionType.NOT_COLLECTED,
        dayOfWeek = DayOfWeek.MONDAY,
        time = time,
    )

    private fun List<AiringScheduleColumnItem>.subjectIds(): List<Int?> = map {
        when (it) {
            is AiringScheduleColumnItem.Data -> it.item.subjectId
            is AiringScheduleColumnItem.CurrentTimeIndicator -> null
            is AiringScheduleColumnItem.PlaceholderData -> error("unexpected placeholder")
        }
    }

    private fun List<AiringScheduleColumnItem>.showTimes(): List<Boolean?> = map {
        (it as? AiringScheduleColumnItem.Data)?.showTime
    }

    @Test
    fun `timed items sorted by time and unknown-time items appended after them`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(23, 0)),
            item(3, LocalTime(1, 0)),
            item(4, null),
            item(5, LocalTime(12, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = false, currentTime = LocalTime(12, 0))

        assertEquals(listOf(3, 5, 2, 1, 4), columns.subjectIds())
    }

    @Test
    fun `unknown-time items come after the current time indicator even when it is late in the day`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(8, 0)),
            item(3, LocalTime(9, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = true, currentTime = LocalTime(23, 59))

        assertEquals(listOf(2, 3, null, 1), columns.subjectIds())
    }

    @Test
    fun `indicator inserted after the last item at or before current time`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(8, 0)),
            item(3, LocalTime(12, 0)),
            item(4, LocalTime(18, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = true, currentTime = LocalTime(12, 0))

        assertEquals(listOf(2, 3, null, 4, 1), columns.subjectIds())
        val indicator = assertIs<AiringScheduleColumnItem.CurrentTimeIndicator>(columns[2])
        assertEquals(LocalTime(12, 0), indicator.currentTime)
    }

    @Test
    fun `only the first unknown-time item shows the time label`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(8, 0)),
            item(3, LocalTime(8, 0)),
            item(4, null),
            item(5, null),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = false, currentTime = LocalTime(0, 0))

        assertEquals(listOf(2, 3, 1, 4, 5), columns.subjectIds())

        assertEquals(listOf(true, false, true, false, false), columns.showTimes())
    }

    @Test
    fun `first unknown-time item shows the time label even when there are no timed items`() {
        val items = listOf(item(1, null), item(2, null))
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = true, currentTime = LocalTime(10, 0))

        assertEquals(listOf(null, 1, 2), columns.subjectIds())
        assertEquals(listOf(null, true, false), columns.showTimes())
    }

    @Test
    fun `timed items keep input order when times are equal`() {
        val items = listOf(
            item(1, LocalTime(8, 0), title = "B"),
            item(2, LocalTime(8, 0), title = "A"),
            item(3, LocalTime(7, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = false, currentTime = LocalTime(0, 0))

        assertEquals(listOf(3, 1, 2), columns.subjectIds())
        assertEquals(listOf(true, true, false), columns.showTimes())
    }

    @Test
    fun `empty list yields only the indicator`() {
        val columns = SchedulePageDataHelper.toColumnItems(emptyList(), addIndicator = true, currentTime = LocalTime(10, 0))
        assertEquals(listOf(null), columns.subjectIds())

        assertEquals(emptyList(), SchedulePageDataHelper.toColumnItems(emptyList(), addIndicator = false, LocalTime(10, 0)))
    }

    private fun episodeWithAiringTime(airingTime: LocalDateTime, timeZone: TimeZone, timeKnown: Boolean) =
        EpisodeWithAiringTime(
            subject = LightSubjectInfo(subjectId = 1, name = "Name", nameCn = "中文名", imageLarge = "img"),
            episode = LightEpisodeInfo(
                episodeId = 2,
                name = "Ep",
                nameCn = "",
                airDate = PackedDate(2026, 9, 4),
                timezone = UTC9,
                sort = EpisodeSort(3),
                ep = EpisodeSort(3),
            ),
            airingTime = airingTime.toInstant(timeZone),
            timeKnown = timeKnown,
        )

    @Test
    fun `toPresentation keeps the time when timeKnown`() {
        val timeZone = TimeZone.of("Asia/Shanghai")
        val presentation = episodeWithAiringTime(LocalDateTime(2026, 9, 4, 23, 30), timeZone, timeKnown = true)
            .toPresentation(timeZone)

        assertEquals(LocalTime(23, 30), presentation.time)
        assertEquals(DayOfWeek.FRIDAY, presentation.dayOfWeek)
        assertEquals("中文名", presentation.subjectTitle)
        assertEquals("Ep", presentation.episodeName)
        assertEquals(EpisodeSort(3), presentation.episodeSort)
    }

    @Test
    fun `toPresentation drops the time but keeps the day when timeKnown is false`() {
        val timeZone = TimeZone.of("Asia/Shanghai")

        val presentation = episodeWithAiringTime(LocalDateTime(2026, 9, 4, 0, 0), timeZone, timeKnown = false)
            .toPresentation(timeZone)

        assertNull(presentation.time)
        assertEquals(DayOfWeek.FRIDAY, presentation.dayOfWeek)
    }

    @Test
    fun `toPresentation converts to the given time zone`() {
        val presentation = episodeWithAiringTime(LocalDateTime(2026, 9, 4, 23, 30), UTC9, timeKnown = true)
            .toPresentation(TimeZone.UTC)

        assertEquals(LocalTime(14, 30), presentation.time)
        assertEquals(DayOfWeek.FRIDAY, presentation.dayOfWeek)
    }

    @Test
    fun `renderTime renders a known time`() {
        assertEquals("09:05", ScheduleItemDefaults.renderTime(null, LocalTime(9, 5)))
        assertEquals("1/2\n23:00", ScheduleItemDefaults.renderTime(LocalDate(2026, 1, 2), LocalTime(23, 0)))
    }

    @Test
    fun `renderTime renders unknown time with the given text`() {

        assertEquals("时间未定", ScheduleItemDefaults.renderTime(null, null, timeUnknownText = "时间未定"))
        assertEquals("Time TBA", ScheduleItemDefaults.renderTime(null, null, timeUnknownText = "Time TBA"))
        assertEquals("1/2\n时间未定", ScheduleItemDefaults.renderTime(LocalDate(2026, 1, 2), null, timeUnknownText = "时间未定"))
    }

    @Test
    fun `renderTime ignores the unknown text when the time is known`() {
        assertEquals("09:05", ScheduleItemDefaults.renderTime(null, LocalTime(9, 5), timeUnknownText = "时间未定"))
        assertEquals(
            "1/2\n23:00",
            ScheduleItemDefaults.renderTime(LocalDate(2026, 1, 2), LocalTime(23, 0), timeUnknownText = "时间未定"),
        )
    }

}
