package com.wynime.app.domain.media.cache

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.tools.Progress
import com.wynime.app.tools.toProgress
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue

interface MediaCache {

    val cacheId: String
        get() {
            return calculateCacheId(
                origin.mediaId,
                metadata,
            )
        }

    val origin: Media

    val metadata: MediaCacheMetadata

    val state: Flow<MediaCacheState>

    val canPlay: Flow<Boolean>
        get() = flowOf(true)

    val downloaderStatus: Flow<DownloaderStatus?>
        get() = flowOf(null)

    suspend fun getCachedMedia(): CachedMedia

    data class FileStats(
        val totalSize: FileSize,

        val downloadedBytes: FileSize,

        val downloadProgress: Progress = if (totalSize.isUnspecified || downloadedBytes.isUnspecified) {
            Progress.Unspecified
        } else {
            if (totalSize.inBytes == 0L) {
                0f.toProgress()
            } else {
                (downloadedBytes.inBytes.toFloat() / totalSize.inBytes).toProgress()
            }
        },

    ) {

        val isDownloadFinished: Boolean get() = downloadProgress.isFinished

        companion object {
            val Unspecified =
                FileStats(
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    Progress.Unspecified,
                )
        }
    }

    val fileStats: Flow<FileStats>

    data class SessionStats(

        val totalSize: FileSize,

        val downloadedBytes: FileSize,

        val downloadSpeed: FileSize,

        val uploadedBytes: FileSize,

        val uploadSpeed: FileSize,

        val downloadProgress: Progress,
    ) {
        companion object {
            val Unspecified =
                SessionStats(
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    FileSize.Unspecified,
                    Progress.Unspecified,
                )
        }
    }

    val sessionStats: Flow<SessionStats>

    suspend fun pause()

    suspend fun close()

    suspend fun resume()

    val isDeleted: StateFlow<Boolean>

    suspend fun closeAndDeleteFiles()

    companion object {
        fun calculateCacheId(originMediaId: String, metadata: MediaCacheMetadata): String {
            val hash = (originMediaId.hashCode() * 31
                    + metadata.subjectId.hashCode() * 31
                    + metadata.episodeId.hashCode()).absoluteValue.toString()
            val subjectName = metadata.subjectNames.firstOrNull() ?: metadata.subjectId
            fun removeSpecials(value: String): String {
                return value.replace(Regex("""[-\\|/.,;'\[\]{}()=_ ~!@#$%^&*]"""), "")
            }
            return "${removeSpecials(subjectName)}-$hash"
        }
    }
}

suspend inline fun MediaCache.isFinished(): Boolean = state.first() == MediaCacheState.COMPLETED

enum class MediaCacheState {
    IN_PROGRESS,
    PAUSED,
    FAILED,
    COMPLETED,
}

open class TestMediaCache(
    val media: CachedMedia,
    override val metadata: MediaCacheMetadata,
    override val sessionStats: MutableStateFlow<MediaCache.SessionStats> =
        MutableStateFlow(
            MediaCache.SessionStats(
                0.bytes,
                0.bytes,
                0.bytes,
                0.bytes,
                0.bytes,
                0f.toProgress(),
            ),
        ),
    override val fileStats: MutableStateFlow<MediaCache.FileStats> =
        MutableStateFlow(
            MediaCache.FileStats(
                FileSize.Unspecified,
                FileSize.Unspecified,
                0f.toProgress(),
            ),
        ),
) : MediaCache {
    override val origin: Media get() = media.origin
    override val state: MutableStateFlow<MediaCacheState> = MutableStateFlow(
        MediaCacheState.IN_PROGRESS,
    )

    override suspend fun getCachedMedia(): CachedMedia = media

    private val resumeCalled = atomic(0)

    @TestOnly
    fun getResumeCalled() = resumeCalled.value

    override suspend fun pause() {
        state.value = MediaCacheState.PAUSED
        println("pause")
    }

    override suspend fun close() {
        println("close")
    }

    override suspend fun resume() {
        resumeCalled.incrementAndGet()
        state.value = MediaCacheState.IN_PROGRESS
        println("resume")
    }

    override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun closeAndDeleteFiles() {
        println("delete called")
        isDeleted.value = true
    }
}
