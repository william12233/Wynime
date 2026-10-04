/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.data.repository.SavedWindowState
import me.him188.ani.app.data.repository.media.MediaSourceSaves
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.app.domain.sourceplugin.InstalledSourcePlugins
import me.him188.ani.app.domain.sourceplugin.SourcePluginRepositoryCache
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.io.SystemPath

// 一个对象, 可都写到 common 里, 不用每个 store 都 expect/actual
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

    /**
     * For [me.him188.ani.app.data.repository.user.TokenRepository]
     * @since 4.9
     */
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

    /**
     * On Android, this store can be shared between the media cache service and app process.
     */
    abstract val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>>

    abstract fun resolveDataStoreFile(name: String): SystemPath

    protected val replaceFileCorruptionHandlerForPreferences: ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler { mutablePreferencesOf() }
}

