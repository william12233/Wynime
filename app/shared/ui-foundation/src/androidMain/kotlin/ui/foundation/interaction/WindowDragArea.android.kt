package com.wynime.app.ui.foundation.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual inline fun WindowDragArea(
    modifier: Modifier,
    crossinline content: @Composable () -> Unit
) {
    Box(modifier) {
        content()
    }
}
