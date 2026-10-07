package com.wynime.app.data.repository.subject

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import com.wynime.app.data.persistent.createTestPreferencesDataStore
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
