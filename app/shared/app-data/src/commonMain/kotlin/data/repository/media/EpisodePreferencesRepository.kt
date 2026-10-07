package com.wynime.app.data.repository.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.persistent.DataStoreJson
import com.wynime.app.data.persistent.database.dao.PreferredWebMediaSource
import com.wynime.app.data.persistent.database.dao.PreferredWebMediaSourceDao
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.mp.KoinPlatform

interface EpisodePreferencesRepository : KoinComponent {

    fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference>
    suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference)

    suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String)

    fun getPreferredWebMediaSource(subjectId: Int): Flow<String?>

    suspend fun removePreferredWebMediaSource(subjectId: Int)
}

class EpisodePreferencesRepositoryImpl(
    private val store: DataStore<Preferences>,
    private val preferredWebMediaSourceDao: PreferredWebMediaSourceDao,
    private val defaultMediaPreference: Flow<MediaPreference> = KoinPlatform.getKoin()
        .get<SettingsRepository>().defaultMediaPreference.flow
) : EpisodePreferencesRepository, KoinComponent {
    private val logger = logger<EpisodePreferencesRepositoryImpl>()
    private val json = DataStoreJson

    override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> {
        return store.data.map {
            it[stringPreferencesKey(subjectId.toString())]
        }.map {
            if (it.isNullOrBlank()) {

                return@map defaultMediaPreference.first()
            }
            val res = kotlin.runCatching {
                json.decodeFromString(MediaPreference.serializer(), it)
            }.getOrNull() ?: defaultMediaPreference.first()

            res
        }
    }

    override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
        logger.info { "Saved user MediaPreference for subject $subjectId: $mediaPreference" }
        store.edit {
            it[stringPreferencesKey(subjectId.toString())] =
                json.encodeToString(MediaPreference.serializer(), mediaPreference)
        }
    }

    override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) {
        logger.info { "Saved user preferred web source for subject $subjectId to $webSourceId" }
        preferredWebMediaSourceDao.setPreferredMediaSource(PreferredWebMediaSource(subjectId, webSourceId))
    }

    override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> {
        return preferredWebMediaSourceDao.getPreferredMediaSourceId(subjectId)
    }

    override suspend fun removePreferredWebMediaSource(subjectId: Int) {
        preferredWebMediaSourceDao.deletePreferredMediaSource(subjectId)
    }
}