package com.wynime.app.ui.download.components

import com.wynime.app.data.models.player.EpisodeHistory
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.download.DownloadSnapshot
import com.wynime.app.tools.Progress
import com.wynime.app.tools.toProgress
import com.wynime.datasources.api.topic.UnifiedCollectionType

internal fun DownloadSnapshot.toDownloadItem(
    collectionType: UnifiedCollectionType?,
    history: EpisodeHistory?,
): DownloadItem {
    val subjectId = metadata.subjectId.toIntOrNull() ?: 0
    val episodeId = metadata.episodeId.toIntOrNull() ?: 0
    return DownloadItem(
        subjectId = subjectId,
        episodeId = episodeId,
        id = id,
        sort = metadata.episodeSort,
        subjectName = metadata.subjectNameCN ?: metadata.subjectNames.firstOrNull().orEmpty(),
        displayName = metadata.episodeName,
        creationTime = metadata.creationTime,
        stats = DownloadItem.Stats(downloadSpeed, progress, totalSize),
        status = toDownloadStatus(status),
        playbackProgress = history.toPlaybackProgress(),
        engineKey = engineKey,
        subjectCollectionType = collectionType,
        playability = when {
            subjectId == 0 || episodeId == 0 -> DownloadItem.Playability.INVALID_SUBJECT_EPISODE_ID
            !canPlay -> DownloadItem.Playability.STREAMING_NOT_SUPPORTED
            else -> DownloadItem.Playability.PLAYABLE
        },
        mediaSourceId = mediaSourceId,
        isBusy = isBusy,
    )
}

internal fun EpisodeHistory?.toPlaybackProgress(): Progress {
    if (this == null || isDeleted || positionMillis <= 0L) return Progress.Unspecified
    val duration = durationMillis?.takeIf { it > 0L } ?: return Progress.Unspecified
    return (positionMillis.toDouble() / duration.toDouble()).toFloat().toProgress()
}

internal fun toDownloadStatus(state: MediaCacheState): DownloadStatus {
    return when (state) {
        MediaCacheState.IN_PROGRESS -> DownloadStatus.IN_PROGRESS
        MediaCacheState.PAUSED -> DownloadStatus.PAUSED
        MediaCacheState.FAILED -> DownloadStatus.FAILED
        MediaCacheState.COMPLETED -> DownloadStatus.COMPLETED
    }
}
