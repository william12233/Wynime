package com.wynime.app.data.persistent.database.converters

import androidx.room.TypeConverter
import kotlin.time.Instant

class InstantConverter {
    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilliseconds()
}
