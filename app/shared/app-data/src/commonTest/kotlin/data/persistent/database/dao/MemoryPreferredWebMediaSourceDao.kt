package com.wynime.app.data.persistent.database.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

fun createMemoryPreferredWebMediaSourceDao(): PreferredWebMediaSourceDao {
    return object : PreferredWebMediaSourceDao {
        private val store = MutableStateFlow(emptyMap<Int, String>())

        override suspend fun setPreferredMediaSource(preferredWebMediaSource: PreferredWebMediaSource) {
            store.value = store.value + (preferredWebMediaSource.subjectId to preferredWebMediaSource.mediaSourceId)
        }

        override fun getPreferredMediaSourceId(subjectId: Int): Flow<String?> {
            return store.map { it[subjectId] }
        }

        override suspend fun deletePreferredMediaSource(subjectId: Int) {
            store.value = store.value - subjectId
        }
    }
}
