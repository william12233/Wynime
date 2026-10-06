/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/wynime-app/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiTrackingSyncSettingsTest {
    @Test
    fun accountBindingSurvivesSettingsStoreRecreation() = runTest {
        val dataStore = createTestPreferencesDataStore()
        val firstStore = BangumiTrackingSyncSettingsStore(dataStore)

        firstStore.bindTokenToAccount("token-family", "id:1060673")

        val recreatedStore = BangumiTrackingSyncSettingsStore(dataStore)
        assertEquals("id:1060673", recreatedStore.accountKeyForToken("token-family"))
    }

    @Test
    fun defaultsMatchTrackingSyncSafetyPolicy() = runTest {
        val settings = BangumiTrackingSyncSettingsStore(createTestPreferencesDataStore()).flow.first()

        assertEquals(false, settings.autoSyncTracking)
        assertEquals(BangumiTrackingConflictPolicy.LATEST_WINS, settings.conflictPolicy)
        assertEquals(true, settings.showSyncResult)
    }
}
