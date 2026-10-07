package com.wynime.app.ui.download.components

import androidx.compose.runtime.Immutable
import kotlin.random.Random
import kotlinx.coroutines.DelicateCoroutinesApi
import com.wynime.app.domain.media.cache.engine.MediaCacheEngineKey
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.tools.Progress
import com.wynime.app.tools.toPercentageOrZero
import com.wynime.app.tools.toProgress
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.format1f

@Immutable
data class DownloadItem(
    val subjectId: Int,
    val episodeId: Int,
    val id: String,
    val sort: EpisodeSort,
    val subjectName: String,
    val displayName: String,
    val creationTime: Long?,
    val stats: Stats,
    val status: DownloadStatus,

    val playbackProgress: Progress = Progress.Unspecified,
    val engineKey: MediaCacheEngineKey?,
    val subjectCollectionType: UnifiedCollectionType?,
    val playability: Playability = Playability.PLAYABLE,

    val mediaSourceId: String? = null,
    val isBusy: Boolean = false,
) {
    enum class Playability {
        PLAYABLE,
        INVALID_SUBJECT_EPISODE_ID,
        STREAMING_NOT_SUPPORTED,
    }

    @Immutable
    data class Stats(
        val downloadSpeed: FileSize,
        val progress: Progress,
        val totalSize: FileSize,
    ) {
        companion object {
            val Unspecified =
                Stats(FileSize.Unspecified, Progress.Unspecified, FileSize.Unspecified)
        }
    }

    val progress get() = stats.progress

    val hasPlaybackProgress get() = !playbackProgress.isUnspecified

    val playbackProgressText: String? = playbackProgress.getOrNull()?.let {
        "${String.format1f(it * 100)}%"
    }

    val isPaused get() = status == DownloadStatus.PAUSED
    val isFailed get() = status == DownloadStatus.FAILED
    val isFinished get() = status == DownloadStatus.COMPLETED

    val totalSize: FileSize get() = stats.totalSize

    val sizeText: String? = totalSize.takeUnless { it.isUnspecified }?.toString()

    val progressText: String? = run {
        val value = stats.progress
        if (value.isUnspecified || this.isFinished) {
            null
        } else {
            "${String.format1f(value.toPercentageOrZero())}%"
        }
    }

    val speedText = run {
        val speed = stats.downloadSpeed
        if (!isFinished && speed != FileSize.Unspecified) {
            return@run "${speed}/s"
        }
        null
    }

    val detailedSizeText: String? = if (isFinished) {
        sizeText
    } else {
        calculateSizeText(stats.totalSize, stats.progress.getOrNull()) ?: sizeText
    }

    val isProgressUnspecified get() = stats.progress.isUnspecified

    val statusFilter: DownloadStatusFilter
        get() = if (isFinished) DownloadStatusFilter.Finished else DownloadStatusFilter.Downloading

    companion object {
        fun calculateSizeText(
            totalSize: FileSize,
            progress: Float?,
        ): String? {
            if (progress == null && totalSize == FileSize.Unspecified) {
                return null
            }
            return when {
                progress == null -> {
                    if (totalSize != FileSize.Unspecified) {
                        "$totalSize"
                    } else null
                }

                totalSize == FileSize.Unspecified -> null

                else -> {
                    "${totalSize * progress} / $totalSize"
                }
            }
        }
    }
}

@TestOnly
fun createTestMediaStats(): MediaStats = MediaStats.Unspecified

@TestOnly
val TestDownloadItems
    get() = listOf(
        createTestDownloadItem(1, "孤独摇滚", "翻转孤独", 1),
        createTestDownloadItem(2, "孤独摇滚", "明天见", 1, initialState = DownloadStatus.PAUSED),
        createTestDownloadItem(
            3,
            "孤独摇滚",
            "火速增员",
            1,
            progress = 1f.toProgress(),
            initialState = DownloadStatus.COMPLETED,
        ),
        createTestDownloadItem(
            4,
            "孤独摇滚",
            "仍在缓冲",
            1,
            initialState = DownloadStatus.FAILED,
            progress = 0.7f.toProgress(),
        ),
    )

@OptIn(DelicateCoroutinesApi::class)
@Suppress("SameParameterValue")
@TestOnly
fun createTestDownloadItem(
    sort: Int,
    subjectName: String = "孤独摇滚",
    displayName: String = "第 $sort 话",
    subjectId: Int = 1,
    episodeId: Int = sort,
    initialState: DownloadStatus? = null,
    downloadSpeed: FileSize = 233.megaBytes,
    progress: Progress = 0.3f.toProgress(),
    totalSize: FileSize = 888.megaBytes,
    mediaSourceId: String? = "AnimeGarden",
    playbackProgress: Progress = Progress.Unspecified,
): DownloadItem {
    val id = Random.nextInt(10000, 99999).toString()
    val resolvedState = initialState ?: when {
        progress.isFinished -> DownloadStatus.COMPLETED
        sort % 2 == 0 -> DownloadStatus.PAUSED
        else -> DownloadStatus.IN_PROGRESS
    }
    return DownloadItem(
        subjectId = subjectId,
        episodeId = episodeId,
        id = id,
        sort = EpisodeSort(sort),
        subjectName = subjectName,
        displayName = displayName,
        creationTime = 100,
        stats = DownloadItem.Stats(
            downloadSpeed = downloadSpeed,
            progress = progress,
            totalSize = totalSize,
        ),
        status = resolvedState,
        playbackProgress = playbackProgress,
        engineKey = MediaCacheEngineKey.WebM3u,
        subjectCollectionType = UnifiedCollectionType.DOING,
        mediaSourceId = mediaSourceId,
    )
}
