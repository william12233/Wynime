package com.wynime.app.ui.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect inline fun WindowDragArea(
    modifier: Modifier = Modifier,
    crossinline content: @Composable () -> Unit = {}
)
