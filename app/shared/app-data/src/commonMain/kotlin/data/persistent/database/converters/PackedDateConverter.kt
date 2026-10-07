package com.wynime.app.data.persistent.database.converters

import androidx.room.TypeConverter
import com.wynime.datasources.api.PackedDate

class PackedDateConverter {
    @TypeConverter
    fun toPackedDate(packed: Int): PackedDate = PackedDate(packed)

    @TypeConverter
    fun fromPackedDate(value: PackedDate): Int = value.packed
}