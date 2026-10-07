package com.wynime.app.ui.update

import androidx.annotation.FloatRange
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.io.files.Path
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.tools.update.FileDownloader
import com.wynime.app.tools.update.FileDownloaderState
import com.wynime.utils.io.inSystem
import com.wynime.utils.platform.annotations.TestOnly

@Stable
data class FileDownloaderStats(
    @param:FloatRange(from = 0.0, to = 1.0)
    val progress: Float,
    val state: FileDownloaderState,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        val Placeholder = FileDownloaderStats(
            progress = 0f,
            state = FileDownloaderState.Idle,
            isPlaceholder = true,
        )
    }
}

class FileDownloaderPresenter(
    fileDownloader: FileDownloader,
    flowScope: CoroutineScope,
) {
    val flow = combine(
        fileDownloader.progress,
        fileDownloader.state,
        ::FileDownloaderStats,
    ).stateIn(
        scope = flowScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = FileDownloaderStats.Placeholder,
    )
}

@TestOnly
object TestFileDownloaderStats {
    @TestOnly
    val Downloading
        get() = FileDownloaderStats(
            progress = 0.5f,
            state = FileDownloaderState.Downloading,
            isPlaceholder = false,
        )

    @TestOnly
    val Succeed
        get() = FileDownloaderStats(
            progress = 1f,
            state = FileDownloaderState.Succeed("", Path("").inSystem, true),
            isPlaceholder = false,
        )

    @TestOnly
    val Failed
        get() = FileDownloaderStats(
            progress = 1f,
            state = FileDownloaderState.Failed(RepositoryNetworkException()),
            isPlaceholder = false,
        )
}
