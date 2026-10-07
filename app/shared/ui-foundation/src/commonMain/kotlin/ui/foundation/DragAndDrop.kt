package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import kotlinx.io.files.Path

@Composable
fun rememberDragAndDropState(
    onEvent: (DragAndDropContent) -> Boolean
): DragAndDropState {
    val hoverState = remember { mutableStateOf(DragAndDropHoverState.NONE) }

    val target = remember {
        object : DragAndDropState {
            override val hoverState: DragAndDropHoverState by hoverState

            override fun onStarted(event: DragAndDropEvent) {
                hoverState.value = DragAndDropHoverState.STARTED
                super.onStarted(event)
            }

            override fun onEntered(event: DragAndDropEvent) {
                hoverState.value = DragAndDropHoverState.ENTERED
                super.onEntered(event)
            }

            override fun onExited(event: DragAndDropEvent) {
                hoverState.value = DragAndDropHoverState.STARTED
                super.onExited(event)
            }

            override fun onEnded(event: DragAndDropEvent) {
                hoverState.value = DragAndDropHoverState.NONE
                super.onEnded(event)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val content = processDragAndDropEventImpl(event)
                return onEvent(content)
            }
        }
    }

    return target
}

expect fun processDragAndDropEventImpl(event: DragAndDropEvent): DragAndDropContent

@Stable
interface DragAndDropState : DragAndDropTarget {
    val hoverState: DragAndDropHoverState

}

@Immutable
enum class DragAndDropHoverState {
    NONE, STARTED, ENTERED
}

@Immutable
sealed interface DragAndDropContent {
    class FileList(val files: List<Path>) : DragAndDropContent

    class PlainText(val content: String) : DragAndDropContent

    object Unsupported : DragAndDropContent
}