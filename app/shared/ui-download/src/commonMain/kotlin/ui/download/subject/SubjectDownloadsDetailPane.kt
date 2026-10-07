package com.wynime.app.ui.download.subject

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.app.ui.download.components.DownloadSelectionState
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider

@Composable
fun SubjectDownloadsDetailPane(
    presenter: SubjectDownloadsPresenter?,
    loadingTitle: String?,
    selectionState: DownloadSelectionState,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    singlePane: Boolean = false,
) {
    if (presenter == null) {
        SubjectDownloadsDetailPaneContent(
            state = SubjectDownloadsUiState(title = loadingTitle),
            actions = SubjectDownloadActions.None,
            sourceInfoProvider = remember { MediaSourceInfoProvider(getSourceInfoFlow = { flowOf(null) }) },
            selectionState = selectionState,
            onPlay = onPlay,
            onViewDetail = onViewDetail,
            modifier = modifier,
            contentPadding = contentPadding,
            singlePane = singlePane,
        )
    } else {
        SubjectDownloadsHost(presenter) { state, actions, sourceInfo ->
            SubjectDownloadsDetailPaneContent(
                state = state,
                actions = actions,
                sourceInfoProvider = sourceInfo,
                selectionState = selectionState,
                onPlay = onPlay,
                onViewDetail = onViewDetail,
                modifier = modifier,
                contentPadding = contentPadding,
                singlePane = singlePane,
            )
        }
    }
}

@Composable
private fun SubjectDownloadsDetailPaneContent(
    state: SubjectDownloadsUiState,
    actions: SubjectDownloadActions,
    sourceInfoProvider: MediaSourceInfoProvider,
    selectionState: DownloadSelectionState,
    onPlay: (DownloadItem) -> Unit,
    onViewDetail: ((DownloadItem) -> Unit)?,
    modifier: Modifier,
    contentPadding: PaddingValues,
    singlePane: Boolean,
) {
    SubjectDownloadsContent(
        state = state,
        selection = selectionState,
        actions = actions,
        sourceInfoProvider = sourceInfoProvider,
        onPlay = onPlay,
        onViewDetail = onViewDetail,
        modifier = modifier,
        rowShape = if (singlePane) RectangleShape else MaterialTheme.shapes.medium,
        contentPadding = contentPadding,
        header = {
            if (singlePane) {
                SubjectDownloadsSummaryRow(
                    downloads = state.downloads,
                    totalEpisodeCount = state.totalEpisodes,
                    inSelection = selectionState.inSelection,
                    selectedEntries = state.downloads.filter { it.id in selectionState.selectedIds },
                    onPauseAll = actions.pauseAll,
                    onResumeAll = actions.resumeAll,
                )
            } else {
                SubjectDownloadsHeader(
                    title = state.title,
                    downloads = state.downloads,
                    totalEpisodeCount = state.totalEpisodes,
                    onPauseAll = actions.pauseAll,
                    onResumeAll = actions.resumeAll,
                )
            }
        },
    )
}
