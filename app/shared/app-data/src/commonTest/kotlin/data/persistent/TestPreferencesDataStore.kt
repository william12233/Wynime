package com.wynime.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

fun createTestPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
): DataStore<Preferences> = MemoryDataStore(initial)
