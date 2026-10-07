package com.wynime.app.tools

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WeekFormatterTest {

    private val fixedInstant = Instant.parse("2024-08-21T12:00:00Z")
    private val testTimeZone = TimeZone.of("Asia/Shanghai")

    private fun createFormatter(now: Instant): WeekFormatter {
        return WeekFormatter(getTimeNow = { now })
    }

    @Test
    fun testFormatToday() {
        val formatter = createFormatter(fixedInstant)
        val result = formatter.format(fixedInstant, testTimeZone)
        assertEquals("今天", result)
    }

    @Test
    fun testFormatTomorrow() {
        val formatter = createFormatter(fixedInstant)
        val result = formatter.format(Instant.parse("2024-08-22T12:00:00Z"), testTimeZone)
        assertEquals("明天", result)
    }

    @Test
    fun testFormatTomorrowLessThan24Hours() {
        val formatter = createFormatter(fixedInstant)
        val result = formatter.format(Instant.parse("2024-08-22T01:00:00Z"), testTimeZone)
        assertEquals("明天", result)
    }

    @Test
    fun testFormatThisWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-24T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("周六", result)
    }

    @Test
    fun testFormatNextWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-28T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("下周三", result)
    }

    @Test
    fun testFormatDifferentYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2023-12-25T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("2023 年 12 月 25 日", result)
    }

    @Test
    fun testFormatDifferentMonthSameYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-11-01T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("11 月 1 日", result)
    }

    @Test
    fun testFormatFutureDateOutsideNextWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-09-15T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("9 月 15 日", result)
    }

    @Test
    fun testFormatPastDate() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-01-01T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("1 月 1 日", result)
    }

    @Test
    fun testFormatNextWeekCrossingYearBoundary() {
        val formatter = createFormatter(Instant.parse("2023-12-29T12:00:00Z"))
        val instance = Instant.parse("2024-01-03T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("下周三", result)
    }

    @Test
    fun testFormatSundayThisWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-25T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("周日", result)
    }

    @Test
    fun testFormatMondayNextWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-26T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("下周一", result)
    }

    @Test
    fun testFormatEndOfYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-12-31T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("12 月 31 日", result)
    }

    @Test
    fun testFormatStartOfYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-01-01T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("1 月 1 日", result)
    }

    @Test
    fun testFormatWithDifferentTimeZone() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-21T12:00:00Z")
        val result = formatter.format(instance, TimeZone.of("America/New_York"))
        assertEquals("今天", result)
    }
}
