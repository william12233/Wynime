package com.wynime.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import com.wynime.app.domain.media.cache.storage.MediaCacheSave
import com.wynime.app.platform.Context
import com.wynime.app.platform.DesktopContext
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.toKtPath

actual fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager =
    PlatformDataStoreManagerDesktop(this as DesktopContext)

val Context.dataStoresDesktop: PlatformDataStoreManagerDesktop get() = dataStores as PlatformDataStoreManagerDesktop

class PlatformDataStoreManagerDesktop(
    private val context: DesktopContext,
) : PlatformDataStoreManager() {
    override val legacyTokenStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(corruptionHandler = replaceFileCorruptionHandlerForPreferences) {
            context.dataStoreDir.resolve("tokens.preferences_pb")
        }
    override val preferencesStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(corruptionHandler = replaceFileCorruptionHandlerForPreferences) {
            context.dataStoreDir.resolve("settings.preferences_pb")
        }
    override val preferredAllianceStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(corruptionHandler = replaceFileCorruptionHandlerForPreferences) {
            context.dataStoreDir.resolve("preferredAllianceStore.preferences_pb")
        }

    override val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>> by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(MediaCacheSave.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { context.dataStoreDir.resolve("mediaCacheMetadataV2") },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
        )
    }

    val firebaseDataStore by lazy {
        DataStoreFactory.create(
            serializer = MapSerializer(
                String.serializer(),
                String.serializer(),
            ).asDataStoreSerializer({ emptyMap() }),
            produceFile = { context.dataStoreDir.resolve("firebaseDataStore") },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyMap() },
        )
    }

    override fun resolveDataStoreFile(name: String): SystemPath = context.dataStoreDir.resolve(name).toKtPath().inSystem
}
