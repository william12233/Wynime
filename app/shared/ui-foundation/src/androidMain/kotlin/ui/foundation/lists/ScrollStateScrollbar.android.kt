package com.wynime.app.ui.foundation.lists

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ScrollStateVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier,
) {
    ScrollStateVerticalScrollIndicator(
        state = state,
        modifier = modifier,
    )
}
