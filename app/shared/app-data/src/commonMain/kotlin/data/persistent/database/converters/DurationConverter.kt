package com.wynime.app.data.persistent.database.converters

import androidx.room.TypeConverter
import kotlin.time.Duration

class DurationConverter {
    @TypeConverter
    fun toDuration(value: String): Duration = Duration.parseIsoString(value)

    @TypeConverter
    fun fromDuration(value: Duration): String = value.toIsoString()
}
