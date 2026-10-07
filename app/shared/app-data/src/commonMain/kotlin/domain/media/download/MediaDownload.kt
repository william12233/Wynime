package com.wynime.app.domain.media.download

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.cache.engine.MediaCacheEngineKey
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.app.tools.Progress
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.coroutines.sampleWithInitial
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

enum class DownloadOperation {
    Pause,
    Resume,
    Delete,
}

data class DownloadSnapshot(
    val id: String,
    val metadata: MediaCacheMetadata,
    val status: MediaCacheState,
    val progress: Progress,
    val totalSize: FileSize,

    val downloadSpeed: FileSize,
    val canPlay: Boolean,
    val mediaSourceId: String,
    val engineKey: MediaCacheEngineKey,

    val operation: DownloadOperation?,
) {
    val isBusy: Boolean get() = operation != null
}

class MediaDownload internal constructor(
    val cache: MediaCache,
    val storage: MediaCacheStorage,
    sharingScope: CoroutineScope,
) {
    val id: String = cache.cacheId
    val metadata: MediaCacheMetadata get() = cache.metadata
    val origin: Media get() = cache.origin
    val engineKey: MediaCacheEngineKey get() = storage.engine.engineKey

    private val scope = sharingScope.childScope()

    private val queuedOperation = MutableStateFlow<DownloadOperation?>(null)

    val operation: StateFlow<DownloadOperation?> = queuedOperation.asStateFlow()

    val snapshot: Flow<DownloadSnapshot> = flow {
        coroutineScope {

            val fileStats = cache.fileStats.shareIn(this, SharingStarted.Lazily, replay = 1)
            val downloadSpeed = fileStats
                .map { stats -> stats.downloadedBytes.takeUnless { it.isUnspecified }?.inBytes ?: 0L }
                .averageRate()
            val transfer = combine(fileStats, downloadSpeed) { stats, speed -> stats to speed }
                .sampleWithInitial(1.seconds)
            emitAll(
                combine(transfer, cache.state, cache.canPlay, queuedOperation) { (stats, speed), state, canPlay, operation ->
                    DownloadSnapshot(
                        id = id,
                        metadata = metadata,
                        status = state,
                        progress = stats.downloadProgress,
                        totalSize = stats.totalSize,
                        downloadSpeed = speed.bytes,
                        canPlay = canPlay,
                        mediaSourceId = origin.mediaSourceId,
                        engineKey = engineKey,
                        operation = operation,
                    )
                },
            )
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        logger.warn(e) { "Snapshot of download $id failed, reporting it as FAILED" }
        emit(
            DownloadSnapshot(
                id = id,
                metadata = metadata,
                status = MediaCacheState.FAILED,
                progress = Progress.Unspecified,
                totalSize = FileSize.Unspecified,
                downloadSpeed = FileSize.Unspecified,
                canPlay = false,
                mediaSourceId = origin.mediaSourceId,
                engineKey = engineKey,
                operation = queuedOperation.value,
            ),
        )
    }.distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = SNAPSHOT_STOP_TIMEOUT_MILLIS), replay = 1)

    suspend fun pause() {
        if (cache.state.first() == MediaCacheState.IN_PROGRESS) cache.pause()
    }

    suspend fun resume() {
        if (cache.state.first() == MediaCacheState.PAUSED) cache.resume()
    }

    internal fun claim(operation: DownloadOperation): Boolean = queuedOperation.compareAndSet(null, operation)

    internal fun release() {
        queuedOperation.value = null
    }

    internal fun close() {
        scope.cancel()
    }

    override fun toString(): String = "MediaDownload(id=$id, engine=${engineKey.key})"

    private companion object {
        private const val SNAPSHOT_STOP_TIMEOUT_MILLIS = 5_000L
        private val logger = logger<MediaDownload>()
    }
}
