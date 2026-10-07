package com.wynime.app.ui.foundation

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.toAndroidDragEvent

actual fun processDragAndDropEventImpl(event: DragAndDropEvent): DragAndDropContent {
    val nativeDragEvent = event.toAndroidDragEvent()
    return DragAndDropContent.Unsupported
}