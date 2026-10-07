package com.wynime.app.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

val LocalTimeFormatter = androidx.compose.runtime.compositionLocalOf<TimeFormatter> {
    error("No TimeFormatter provided")
}

@Composable
fun formatDateTime(
    timestamp: Long,
    showTime: Boolean = true,
): String {
    val formatter by rememberUpdatedState(LocalTimeFormatter.current)
    return remember(timestamp, showTime) {
        if (timestamp == 0L) ""
        else formatter.format(timestamp, showTime)
    }
}

@Composable
fun formatDateTime(
    dateTime: LocalDateTime,
    showTime: Boolean = true,
): String {
    val formatter by rememberUpdatedState(LocalTimeFormatter.current)
    return remember(dateTime, showTime) {
        val instant = dateTime.toInstant(TimeZone.currentSystemDefault())
        if (instant.toEpochMilliseconds() == 0L) ""
        else formatter.format(instant, showTime)
    }
}

@Composable
fun formatDateAsWeek(
    timestamp: Long,
    showTime: Boolean = true,
): String {
    return remember(timestamp, showTime) {
        if (timestamp == 0L) ""
        else WeekFormatter.System.format(Instant.fromEpochMilliseconds(timestamp))
    }
}
