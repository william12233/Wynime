package com.wynime.app.ui.download.subject

import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.models.episode.nameOrNameCn
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.app.ui.download.components.DownloadItem

internal fun SubjectCollectionInfo.downloadEpisodes(): List<EpisodeDownloadItem> = episodes.map {
    EpisodeDownloadItem(
        it.episodeId,
        it.episodeInfo.sort,
        it.episodeInfo.displayName,
        it.collectionType,
        it.episodeInfo.isKnownCompleted(recurrence),
        originalTitle = it.episodeInfo.nameOrNameCn,
    )
}

fun buildSubjectDownloadItems(
    episodes: List<EpisodeDownloadItem>,
    downloads: List<DownloadItem>,
): List<SubjectDownloadListItem> {
    val byEpisode = downloads.distinctBy { it.id }.groupBy { it.episodeId }
    val episodeIds = episodes.mapTo(hashSetOf()) { it.episodeId }
    val items = episodes.distinctBy { it.episodeId }.map { episode ->
        byEpisode[episode.episodeId]?.map { SubjectDownloadListItem.Download(it) }
            ?: listOf(SubjectDownloadListItem.Episode(episode))
    }.flatten() + byEpisode.filterKeys { it !in episodeIds }.values.flatten().map { SubjectDownloadListItem.Download(it) }
    return items.sortedBy {
        when (it) {
            is SubjectDownloadListItem.Episode -> it.episode.sort
            is SubjectDownloadListItem.Download -> it.download.sort
        }
    }
}
