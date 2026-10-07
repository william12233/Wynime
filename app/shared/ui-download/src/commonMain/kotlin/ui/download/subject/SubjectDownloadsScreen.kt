package com.wynime.app.ui.download.subject

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.app.ui.download.components.rememberDownloadSelectionState
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets

@Composable
fun SubjectDownloadsScreen(
    vm: SubjectDownloadsViewModel,
    onPlay: (DownloadItem) -> Unit,
    onNavigateDownloadDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
    navigationIcon: @Composable () -> Unit = {},
) {
    val selection = rememberDownloadSelectionState()
    SubjectDownloadsHost(vm.presenter) { state, actions, sourceInfo ->
        SubjectDownloadsPage(
            state = state,
            selection = selection,
            actions = actions,
            sourceInfoProvider = sourceInfo,
            onPlay = onPlay,
            onViewDetail = { onNavigateDownloadDetail(it.id) },
            modifier = modifier,
            windowInsets = windowInsets,
            navigationIcon = navigationIcon,
        )
    }
}
