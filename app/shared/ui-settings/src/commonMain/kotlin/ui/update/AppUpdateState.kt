package com.wynime.app.ui.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.utils.io.SystemPath

@Stable
sealed interface AppUpdateState {

    @Immutable
    data object ClickToCheck : AppUpdateState

    @Immutable
    data object AlreadyUpToDate : AppUpdateState

    sealed interface HasNewVersion : AppUpdateState {
        val version: NewVersion
    }

    @Immutable
    data class HasUpdate(override val version: NewVersion) : AppUpdateState, HasNewVersion

    @Stable
    data class Downloading(
        override val version: NewVersion,
        private val fileDownloaderStats: FileDownloaderStats,
    ) : HasNewVersion {
        val progress: Float get() = fileDownloaderStats.progress
    }

    @Stable
    data class DownloadFailed(
        override val version: NewVersion,
        val throwable: Throwable,
    ) : HasNewVersion

    @Immutable
    data class Downloaded(
        override val version: NewVersion,
        val file: SystemPath,
    ) : HasNewVersion

    @Immutable
    data class Installing(override val version: NewVersion) : HasNewVersion

    @Immutable
    data class WaitingForPermission(override val version: NewVersion) : HasNewVersion

    companion object
}
