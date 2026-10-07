package com.wynime.app.ui.download.components

import androidx.compose.runtime.Immutable

@Immutable
enum class DownloadStatus {
    IN_PROGRESS,
    PAUSED,
    FAILED,
    COMPLETED,
}
