package com.wynime.app.data.persistent.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.wynime.app.platform.Context
import com.wynime.app.platform.DesktopContext

actual fun Context.createDatabaseBuilder(): RoomDatabase.Builder<WynimeDatabase> {
    this as DesktopContext

    return Room.databaseBuilder<WynimeDatabase>(
        name = dataDir.resolve("ani_room_database_main.db").absolutePath,
    )
}