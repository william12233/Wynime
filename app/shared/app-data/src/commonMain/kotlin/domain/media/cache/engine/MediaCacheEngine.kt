package com.wynime.app.domain.media.cache.engine

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.storage.MediaCacheSave
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline

interface MediaCacheEngine {

    val engineKey: MediaCacheEngineKey

    val stats: Flow<MediaStats>

    fun supports(media: Media): Boolean

    @Composable
    fun ComposeContent() {
    }

    suspend fun restore(
        origin: Media,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext
    ): MediaCache?

    suspend fun createCache(
        origin: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        parentContext: CoroutineContext,
    ): MediaCache

    suspend fun deleteUnusedCaches(all: List<MediaCache>)
}

@Serializable
@JvmInline
value class MediaCacheEngineKey(val key: String) {
    companion object {
        val WebM3u = MediaCacheEngineKey("web-m3u")
    }
}
