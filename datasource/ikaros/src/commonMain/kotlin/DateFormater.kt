package com.wynime.datasources.ikaros

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal object DateFormater {
    fun utcDateStr2timeStamp(dateStr: String): Long {
        if (dateStr.isEmpty()) {
            return 0
        }
        return LocalDateTime.parse(dateStr).toInstant(TimeZone.UTC).toEpochMilliseconds()
    }
}
