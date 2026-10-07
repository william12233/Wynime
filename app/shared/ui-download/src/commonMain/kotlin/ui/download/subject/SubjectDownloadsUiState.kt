package com.wynime.app.ui.download.subject

import androidx.compose.runtime.Immutable
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Immutable
data class EpisodeDownloadItem(
    val episodeId: Int,
    val sort: EpisodeSort,
    val title: String,
    val watchStatus: UnifiedCollectionType,
    val hasPublished: Boolean,

    val originalTitle: String = title,
)

sealed interface SubjectDownloadListItem {
    val key: String

    data class Episode(val episode: EpisodeDownloadItem) : SubjectDownloadListItem {
        override val key = "episode-${episode.episodeId}"
    }

    data class Download(val download: DownloadItem) : SubjectDownloadListItem {
        override val key = "download-${download.id}"
    }
}

@Immutable
data class DownloadRequestUiState(
    val episodeIds: Set<Int> = emptySet(),
    val busy: Boolean = false,
    val canCancel: Boolean = false,
)

@Immutable
data class SubjectDownloadsUiState(
    val title: String? = null,
    val items: List<SubjectDownloadListItem> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val totalEpisodes: Int? = null,
    val episodesLoading: Boolean = true,
    val downloadsLoading: Boolean = true,
    val episodesFailed: Boolean = false,
    val downloadsFailed: Boolean = false,
    val request: DownloadRequestUiState = DownloadRequestUiState(),
)
