package com.wynime.app.data.persistent.database

import androidx.room.RoomDatabase
import com.wynime.app.platform.Context

expect fun Context.createDatabaseBuilder(): RoomDatabase.Builder<WynimeDatabase>