package com.wynime.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.builtins.ListSerializer
import com.wynime.app.domain.media.cache.storage.MediaCacheSave
import com.wynime.app.platform.Context
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.toFile
import com.wynime.utils.io.toKtPath

actual fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager = PlatformDataStoreManagerAndroid(this)

internal class PlatformDataStoreManagerAndroid(
    private val context: Context,
) : PlatformDataStoreManager() {
    override fun resolveDataStoreFile(name: String): SystemPath {
        return context.applicationContext.dataStoreFile(name).toKtPath().inSystem
    }

    private val Context.legacyTokenStoreImpl by preferencesDataStore(
        "tokens",
        corruptionHandler = replaceFileCorruptionHandlerForPreferences,
    )
    override val legacyTokenStore: DataStore<Preferences> get() = context.legacyTokenStoreImpl

    private val Context.preferencesStoreImpl by preferencesDataStore(
        "preferences",
        corruptionHandler = replaceFileCorruptionHandlerForPreferences,
    )
    override val preferencesStore: DataStore<Preferences> get() = context.preferencesStoreImpl

    private val Context.preferredAlliancesStoreImpl by preferencesDataStore(
        "preferredAlliances",
        corruptionHandler = replaceFileCorruptionHandlerForPreferences,
    )
    override val preferredAllianceStore: DataStore<Preferences> get() = context.preferredAlliancesStoreImpl

    private val Context.mediaCacheMetadataStoreImpl by lazy {
        MultiProcessDataStoreFactory.create(
            serializer = ListSerializer(MediaCacheSave.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("mediaCacheMetadataV2").toFile() },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
        )
    }

    override val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>>
        get() = context.mediaCacheMetadataStoreImpl
}
