package com.wynime.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import com.wynime.app.data.models.user.SelfInfo
import com.wynime.app.data.repository.SavedWindowState
import com.wynime.app.data.repository.media.MediaSourceSaves
import com.wynime.app.data.repository.player.EpisodeHistories
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.app.domain.sourceplugin.InstalledSourcePlugins
import com.wynime.app.domain.sourceplugin.SourcePluginRepositoryCache
import com.wynime.app.domain.media.cache.storage.MediaCacheSave
import com.wynime.utils.httpdownloader.DownloadState
import com.wynime.utils.io.SystemPath

abstract class PlatformDataStoreManager {
    val mediaSourceSaveStore by lazy {
        DataStoreFactory.create(
            serializer = MediaSourceSaves.serializer()
                .asDataStoreSerializer({ MediaSourceSaves.Default }),
            produceFile = { resolveDataStoreFile("mediaSourceSaves") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                MediaSourceSaves.Default
            },
        )
    }

    val episodeHistoryStore by lazy {
        DataStoreFactory.create(
            serializer = EpisodeHistories.serializer()
                .asDataStoreSerializer({ EpisodeHistories.Empty }),
            produceFile = { resolveDataStoreFile("episodeHistories") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                EpisodeHistories.Empty
            },
        )
    }

    val savedWindowStateStore by lazy {
        DataStoreFactory.create(
            serializer = SavedWindowState.serializer().nullable
                .asDataStoreSerializer({ null }),
            produceFile = { resolveDataStoreFile("windowState") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                null
            },
        )
    }

    val m3u8DownloaderStore by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(DownloadState.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("m3u8Downloader") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                emptyList()
            },
        )
    }

    val tokenStore by lazy {
        DataStoreFactory.create(
            serializer = TokenSave.serializer().asDataStoreSerializer({ TokenSave.Initial }),
            produceFile = { resolveDataStoreFile("authSession") },
            corruptionHandler = ReplaceFileCorruptionHandler { TokenSave.Initial },
        )
    }

    val selfInfoStore by lazy {
        DataStoreFactory.create(
            serializer = SelfInfo.serializer().nullable.asDataStoreSerializer({ null }),
            produceFile = { resolveDataStoreFile("selfInfo") },
            corruptionHandler = ReplaceFileCorruptionHandler { null },
        )
    }

    val installedSourcePluginsStore by lazy {
        DataStoreFactory.create(
            serializer = InstalledSourcePlugins.serializer().asDataStoreSerializer({ InstalledSourcePlugins.Empty }),
            produceFile = { resolveDataStoreFile("installedSourcePlugins") },
            corruptionHandler = ReplaceFileCorruptionHandler { InstalledSourcePlugins.Empty },
        )
    }

    val sourcePluginRepositoryCacheStore by lazy {
        DataStoreFactory.create(
            serializer = SourcePluginRepositoryCache.serializer()
                .asDataStoreSerializer({ SourcePluginRepositoryCache() }),
            produceFile = { resolveDataStoreFile("sourcePluginRepositoryCache") },
            corruptionHandler = ReplaceFileCorruptionHandler { SourcePluginRepositoryCache() },
        )
    }

    abstract val legacyTokenStore: DataStore<Preferences>
    abstract val preferencesStore: DataStore<Preferences>
    abstract val preferredAllianceStore: DataStore<Preferences>

    abstract val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>>

    abstract fun resolveDataStoreFile(name: String): SystemPath

    protected val replaceFileCorruptionHandlerForPreferences: ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler { mutablePreferencesOf() }
}

