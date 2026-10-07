package com.wynime.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.serialization.json.Json

@Composable
fun rememberWynimeBackStack(initialRoute: NavRoutes): SnapshotStateList<NavRoutes> =
    rememberSaveable(saver = WynimeBackStackSaver) { mutableStateListOf(initialRoute) }

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val WynimeBackStackSaver: Saver<SnapshotStateList<NavRoutes>, Any> = listSaver(
    save = { stack -> stack.map { json.encodeToString(NavRoutes.serializer(), it) } },
    restore = { saved ->

        if (saved.isEmpty()) {
            null
        } else {
            saved.map { json.decodeFromString(NavRoutes.serializer(), it) }.toMutableStateList()
        }
    },
)
