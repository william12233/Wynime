package com.wynime.app.data.persistent.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

fun createTestWynimeDatabase(
    queryCoroutineContext: CoroutineContext = Dispatchers.Default,
): WynimeDatabase {
    return Room.inMemoryDatabaseBuilder<WynimeDatabase> { WynimeDatabaseConstructor.initialize() }
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryCoroutineContext)
        .build()
}
