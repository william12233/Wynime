package com.wynime.app.ui.exploration

import kotlinx.datetime.TimeZone
import com.wynime.app.data.network.BangumiCalendarDay
import com.wynime.app.data.network.BangumiCalendarEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TodayUpdatesTest {
    @Test
    fun `today mapping keeps every anime item and applies calendar rules`() {
        val anime = (1..18).map { id ->
            BangumiCalendarEntry(
                id = 1000 + id,
                name = "Original $id",
                nameCn = if (id == 1) "中文標題" else "",
                imageLarge = if (id == 2) "cover-$id" else "",
                type = 2,
            )
        }
        val items = listOf(
            BangumiCalendarDay(weekdayId = 2, items = anime + anime.first()),
            BangumiCalendarDay(
                weekdayId = 2,
                items = listOf(
                    BangumiCalendarEntry(0, "invalid", "", "", type = 2),
                    BangumiCalendarEntry(2000, "book", "", "", type = 1),
                    BangumiCalendarEntry(2001, "unknown", "", "", type = 0),
                ),
            ),
            BangumiCalendarDay(weekdayId = 1, items = anime),
        )

        val mapped = items.toTodayUpdateSubjectInfos(weekdayId = 2)

        assertEquals(18, mapped.size)
        assertEquals((1001..1018).toList(), mapped.map { it.bangumiId })
        assertEquals("中文標題", mapped.first().displayName)
        assertEquals("Original 2", mapped[1].displayName)
        assertEquals("cover-2", mapped[1].imageLarge)
    }

    @Test
    fun `next refresh delay uses local midnight`() {
        val timeZone = TimeZone.of("Asia/Taipei")

        assertEquals(
            1.seconds,
            delayUntilNextMidnight(Instant.parse("2026-10-06T15:59:59Z"), timeZone),
        )
        assertEquals(
            24.hours,
            delayUntilNextMidnight(Instant.parse("2026-10-06T16:00:00Z"), timeZone),
        )
    }
}
