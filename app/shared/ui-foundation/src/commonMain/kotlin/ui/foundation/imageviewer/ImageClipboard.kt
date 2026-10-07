package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.runtime.Composable

fun interface ImageClipboard {

    suspend fun copy(file: ImageViewerExportedFile)
}

@Composable
expect fun rememberImageClipboard(): ImageClipboard?
