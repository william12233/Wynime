package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable

@Composable
actual fun ImageViewer(handler: ImageViewerHandler, onClose: () -> Unit) {
    ImageViewerOverlay(handler, onClose)
}

@Composable
actual fun ImageViewerBackHandler(handler: ImageViewerHandler) {
    ImageViewerOverlayBackHandler(handler)
}
