package com.wynime.app.ui.download.components

import androidx.compose.runtime.Immutable
import com.wynime.app.tools.getOrZero
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@Immutable
data class SubjectDownloadGroup(
    val subjectId: Int,
    val subjectName: String,
    val entries: List<DownloadItem>,
    val collectionType: UnifiedCollectionType?,

    val imageUrl: String? = null,

    val totalEpisodeCount: Int? = null,
) {
    val key = subjectId.toString()

    val finishedCount: Int = entries.filter { it.isFinished }.map { it.episodeId }.distinct().size
    val downloadingCount: Int = entries.count { !it.isFinished }
    val averageProgress: Float =
        entries.map { it.progress.getOrZero() }.ifEmpty { listOf(0f) }.average().toFloat()

    val displayTotalCount: Int = totalEpisodeCount?.coerceAtLeast(entries.map { it.episodeId }.distinct().size) ?: entries.map { it.episodeId }.distinct().size

    val activeDownloadCount: Int = entries.count { it.status == DownloadStatus.IN_PROGRESS }

    val totalSize: FileSize = run {
        var sum = 0L
        var any = false
        entries.forEach { entry ->
            if (entry.totalSize != FileSize.Unspecified) {
                sum += entry.totalSize.inBytes
                any = true
            }
        }
        if (any) sum.bytes else FileSize.Unspecified
    }

    val downloadSpeed: FileSize = run {
        var sum = 0L
        var any = false
        entries.forEach { entry ->
            if (entry.status == DownloadStatus.IN_PROGRESS &&
                entry.stats.downloadSpeed != FileSize.Unspecified
            ) {
                sum += entry.stats.downloadSpeed.inBytes
                any = true
            }
        }
        if (any) sum.bytes else FileSize.Unspecified
    }

    val downloadSpeedText: String? =
        if (downloadSpeed != FileSize.Unspecified) "↓ $downloadSpeed/s" else null

    val hasUnfinished: Boolean = entries.any { !it.isFinished }
}

@TestOnly
internal val TestCacheGroupSates = listOf(
    SubjectDownloadGroup(
        subjectId = 1,
        subjectName = "孤独摇滚",
        entries = TestDownloadItems,
        collectionType = UnifiedCollectionType.DOING,
        totalEpisodeCount = 12,
    ),
)
