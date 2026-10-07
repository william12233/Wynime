package com.wynime.app.ui.foundation.interaction

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

fun Modifier.keyboardDirectionToSelectItem(
    selectedItemIndex: () -> Int,
    itemCount: () -> Int,
    onSelect: suspend (Int) -> Unit,
) = composed {
    val scope = rememberCoroutineScope()
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyUp) {
            when (it.key) {
                Key.DirectionUp -> {
                    scope.launch {
                        calculateDirectionSelectionIndex(
                            selectedItemIndex = selectedItemIndex(),
                            itemCount = itemCount(),
                            delta = -1,
                        )?.let { newIndex ->
                            onSelect(newIndex)
                        }
                    }
                    true
                }

                Key.DirectionDown -> {
                    scope.launch {
                        calculateDirectionSelectionIndex(
                            selectedItemIndex = selectedItemIndex(),
                            itemCount = itemCount(),
                            delta = 1,
                        )?.let { newIndex ->
                            onSelect(newIndex)
                        }
                    }
                    true
                }

                else -> false
            }
        } else false
    }
}

internal fun calculateDirectionSelectionIndex(
    selectedItemIndex: Int,
    itemCount: Int,
    delta: Int,
): Int? {
    if (itemCount <= 0) return null

    val currentIndex = selectedItemIndex.coerceAtLeast(-1)
    val newIndex = if (currentIndex == -1) {
        0
    } else {
        (currentIndex + delta).coerceIn(0, itemCount - 1)
    }
    return newIndex.takeIf { it != selectedItemIndex }
}

fun Modifier.keyboardPageToScroll(
    height: () -> Float,
    onScrollBy: suspend (px: Float) -> Unit,
) = composed {
    val scope = rememberCoroutineScope()
    onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyUp) {
            when (it.key) {
                Key.PageDown -> {
                    scope.launch { onScrollBy(height()) }
                    true
                }

                Key.PageUp -> {
                    scope.launch { onScrollBy(-height()) }
                    true
                }

                else -> false
            }
        } else false
    }
}
