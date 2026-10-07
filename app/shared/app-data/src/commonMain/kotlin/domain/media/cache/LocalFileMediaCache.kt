package com.wynime.app.domain.media.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.tools.Progress
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.length

class LocalFileMediaCache(
    override val origin: Media,
    override val metadata: MediaCacheMetadata,
    val file: SystemPath,
    uploadedSize: FileSize = 0.bytes,
    private val backedMediaSourceId: String = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
    private val onCloseAndDeleteFiles: LocalFileMediaCache.(SystemPath) -> Unit = { file.deleteRecursively() },
) : MediaCache {
    override val state: Flow<MediaCacheState> = MutableStateFlow(MediaCacheState.COMPLETED)
    override val canPlay: Flow<Boolean> = MutableStateFlow(true)

    private val fileSize = file.length().bytes

    override val fileStats: Flow<MediaCache.FileStats> = MutableStateFlow(
        MediaCache.FileStats(fileSize, fileSize, Progress.fromZeroToOne(1f)),
    )

    override val sessionStats: Flow<MediaCache.SessionStats> = MutableStateFlow(
        MediaCache.SessionStats(
            totalSize = fileSize,
            downloadedBytes = fileSize,
            downloadSpeed = 0.bytes,
            uploadedBytes = uploadedSize,
            uploadSpeed = 0.bytes,
            downloadProgress = Progress.fromZeroToOne(1f),
        ),
    )

    private val _isDeleted = MutableStateFlow(false)
    override val isDeleted: StateFlow<Boolean> = _isDeleted

    override suspend fun getCachedMedia(): CachedMedia {
        return CachedMedia(origin, backedMediaSourceId, ResourceLocation.LocalFile(file.absolutePath))
    }

    override suspend fun pause() {

    }

    override suspend fun close() {

    }

    override suspend fun resume() {

    }

    override suspend fun closeAndDeleteFiles() {
        _isDeleted.value = true
        onCloseAndDeleteFiles(file)
    }
}
