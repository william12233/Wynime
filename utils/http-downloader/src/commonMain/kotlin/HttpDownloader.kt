package com.wynime.utils.httpdownloader

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

interface HttpDownloader : AutoCloseable {

    val progressFlow: Flow<DownloadProgress>

    fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress>

    val downloadStatesFlow: Flow<List<DownloadState>>

    suspend fun init()

    suspend fun download(
        url: String,
        options: DownloadOptions = DownloadOptions(),
    ): DownloadId

    suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        options: DownloadOptions = DownloadOptions(),
    ): DownloadState?

    suspend fun resume(downloadId: DownloadId): Boolean

    suspend fun getActiveDownloadIds(): List<DownloadId>

    suspend fun pause(downloadId: DownloadId): Boolean

    suspend fun pauseAll(): List<DownloadId>

    suspend fun cancel(downloadId: DownloadId): Boolean

    suspend fun cancelAll()

    suspend fun remove(downloadId: DownloadId): Boolean

    suspend fun getState(downloadId: DownloadId): DownloadState?

    suspend fun getAllStates(): List<DownloadState>

    override fun close()
}

@JvmInline
@Serializable
value class DownloadId(val value: String) {
    override fun toString(): String = value
}

@Serializable
enum class DownloadErrorCode {
    NO_MEDIA_LIST,
    UNEXPECTED_ERROR,
}

@Serializable
data class DownloadError(
    val code: DownloadErrorCode,
    val technicalMessage: String? = null,
)

@Serializable
data class DownloadProgress(
    val downloadId: DownloadId,
    val url: String,
    val mediaType: MediaType,
    val totalSegments: Int,
    val downloadedSegments: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val error: DownloadError? = null,

    val lastSegmentFailure: SegmentFailure? = null,
)

@Serializable
data class SegmentFailure(

    val segmentIndex: Int?,
    val attempt: Int,
    val maxAttempts: Int,
    val message: String,
    val timestampMillis: Long,
)

@Serializable
enum class DownloadStatus {
    INITIALIZING,
    DOWNLOADING,
    MERGING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

@Entity(
    tableName = "http_cache_download_state",
    primaryKeys = ["downloadId"],
    indices = [
        Index(value = ["downloadId"], unique = true),
    ],
)
@Serializable
data class DownloadState(
    @field:TypeConverters(DownloadIdConverter::class)
    val downloadId: DownloadId,
    val url: String,
    @ColumnInfo("path")
    @SerialName("outputPath")
    val relativeOutputPath: String,
    @field:TypeConverters(SegmentInfoListConverter::class)
    val segments: List<SegmentInfo>,
    val totalSegments: Int,
    val downloadedBytes: Long,
    val timestamp: Long,
    @field:TypeConverters(DownloadStatusConverter::class)
    val status: DownloadStatus,
    @Embedded(prefix = "error_")
    val error: DownloadError? = null,
    @SerialName("segmentCacheDir")
    @ColumnInfo("segmentDir")
    val relativeSegmentCacheDir: String,
    @field:TypeConverters(StringMapConverter::class)
    val requestHeaders: Map<String, String>,
    val mediaType: MediaType,
)

@Serializable
enum class MediaType {
    M3U8, MP4, MKV;

    val outputFileExtension: String
        get() = when (this) {
            M3U8 -> ".mp4"
            MP4 -> ".mp4"
            MKV -> ".mkv"
        }
}

@Serializable
data class SegmentInfo(
    val index: Int,
    val url: String,
    val isDownloaded: Boolean,
    val byteSize: Long = -1,
    val durationSeconds: Float? = null,
    val title: String? = null,
    val isDiscontinuity: Boolean = false,
    val encryption: SegmentEncryptionInfo? = null,
    @SerialName("tempFilePath")
    val relativeTempFilePath: String,
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
)

@Serializable
data class SegmentEncryptionInfo(
    val method: String,
    val keyUri: String,
    val iv: String? = null,
)

@Serializable
data class DownloadOptions(
    val maxConcurrentSegments: Int = 3,
    val segmentRetryCount: Int = 3,
    val connectTimeoutMs: Long = 30_000,
    val readTimeoutMs: Long = 30_000,
    val autoSaveIntervalMs: Long = 5_000,
    val headers: Map<String, String> = emptyMap(),
    val maxRetriesPerSegment: Int = 100,
    val baseRetryDelayMillis: Long = 1000L,
)
