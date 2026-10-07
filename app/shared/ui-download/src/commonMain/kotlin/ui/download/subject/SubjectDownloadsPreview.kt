package com.wynime.app.ui.download.subject

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.wynime.app.ui.download.components.TestDownloadItems
import com.wynime.app.ui.download.components.rememberDownloadSelectionState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.mediafetch.rememberTestMediaSourceInfoProvider
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@Preview
@Composable
@OptIn(TestOnly::class)
private fun SubjectDownloadsPreview() = ProvideCompositionLocalsForPreview {
    val downloads = TestDownloadItems
    val episodes = listOf(EpisodeDownloadItem(5, EpisodeSort(5), "新的剧集", UnifiedCollectionType.DOING, true))
    SubjectDownloadsPage(
        state = SubjectDownloadsUiState(
            title = "孤独摇滚",
            items = buildSubjectDownloadItems(episodes, downloads),
            downloads = downloads,
            totalEpisodes = 12,
            episodesLoading = false,
            downloadsLoading = false,
        ),
        selection = rememberDownloadSelectionState(),
        actions = SubjectDownloadActions({}, {}, {}, {}, {}, {}, {}, {}),
        sourceInfoProvider = rememberTestMediaSourceInfoProvider(),
        onPlay = {},
        onViewDetail = null,
    )
}
