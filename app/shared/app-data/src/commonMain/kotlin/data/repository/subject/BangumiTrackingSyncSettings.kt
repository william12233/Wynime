/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BangumiTrackingSyncSettings(
    val autoSyncTracking: Boolean = false,
    val conflictPolicy: BangumiTrackingConflictPolicy = BangumiTrackingConflictPolicy.LATEST_WINS,
    val showSyncResult: Boolean = true,
)

class BangumiTrackingSyncSettingsStore(
    private val preferences: DataStore<Preferences>,
) {
    val flow: Flow<BangumiTrackingSyncSettings> = preferences.data
        .map { values ->
            BangumiTrackingSyncSettings(
                autoSyncTracking = values[AUTO_SYNC] ?: false,
                conflictPolicy = values[CONFLICT_POLICY]
                    ?.let { runCatching { BangumiTrackingConflictPolicy.valueOf(it) }.getOrNull() }
                    ?: BangumiTrackingConflictPolicy.LATEST_WINS,
                showSyncResult = values[SHOW_RESULT] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setAutoSyncTracking(value: Boolean) {
        preferences.edit { it[AUTO_SYNC] = value }
    }

    suspend fun setConflictPolicy(value: BangumiTrackingConflictPolicy) {
        preferences.edit { it[CONFLICT_POLICY] = value.name }
    }

    suspend fun setShowSyncResult(value: Boolean) {
        preferences.edit { it[SHOW_RESULT] = value }
    }

    /**
     * Resolves an OAuth token family to the stable, id-scoped Bangumi metadata key. The binding
     * is persisted separately from the Room rows so a refreshed access token can still find local
     * tombstones after the application restarts without issuing a network request.
     */
    suspend fun accountKeyForToken(tokenKey: String): String? = preferences.data
        .first()[ACCOUNT_BINDINGS]
        .parseBindings()[tokenKey]

    suspend fun bindTokenToAccount(tokenKey: String, accountKey: String) {
        preferences.edit { values ->
            val bindings = values[ACCOUNT_BINDINGS].parseBindings().toMutableMap()
            bindings[tokenKey] = accountKey
            values[ACCOUNT_BINDINGS] = bindings.encodeBindings()
        }
    }

    suspend fun update(update: BangumiTrackingSyncSettings.() -> BangumiTrackingSyncSettings) {
        preferences.edit { values ->
            val current = BangumiTrackingSyncSettings(
                autoSyncTracking = values[AUTO_SYNC] ?: false,
                conflictPolicy = values[CONFLICT_POLICY]
                    ?.let { runCatching { BangumiTrackingConflictPolicy.valueOf(it) }.getOrNull() }
                    ?: BangumiTrackingConflictPolicy.LATEST_WINS,
                showSyncResult = values[SHOW_RESULT] ?: true,
            ).update()
            values[AUTO_SYNC] = current.autoSyncTracking
            values[CONFLICT_POLICY] = current.conflictPolicy.name
            values[SHOW_RESULT] = current.showSyncResult
        }
    }

    private companion object {
        val AUTO_SYNC = booleanPreferencesKey("bangumi_tracking_auto_sync")
        val CONFLICT_POLICY = stringPreferencesKey("bangumi_tracking_conflict_policy")
        val SHOW_RESULT = booleanPreferencesKey("bangumi_tracking_show_result")
        val ACCOUNT_BINDINGS = stringPreferencesKey("bangumi_tracking_account_bindings")
    }
}

private fun String?.parseBindings(): Map<String, String> = orEmpty()
    .split(';')
    .asSequence()
    .mapNotNull { entry ->
        val separator = entry.indexOf('=')
        if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
        entry.substring(0, separator) to entry.substring(separator + 1)
    }
    .toMap()

private fun Map<String, String>.encodeBindings(): String = entries.joinToString(";") { (token, account) ->
    "$token=$account"
}
