package com.wynime.app.ui.foundation.lists

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun LazyListVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val style = remember(colorScheme) {
        ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 8.dp,
            shape = RoundedCornerShape(8.dp),
            hoverDurationMillis = 250,
            unhoverColor = colorScheme.onSurface.copy(alpha = 0.30f),
            hoverColor = colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
    VerticalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(scrollState = state),
        style = style,
    )
}
