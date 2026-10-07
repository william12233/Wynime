package com.wynime.app.data.persistent.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.wynime.app.platform.Context

actual fun Context.createDatabaseBuilder(): RoomDatabase.Builder<WynimeDatabase> {
    return Room.databaseBuilder<WynimeDatabase>(
        context = applicationContext,
        name = applicationContext.getDatabasePath("ani_room_database_main.db").absolutePath,
    )
}