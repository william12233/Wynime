package com.wynime.app.ui.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun Color.looming(): Color {
    return copy(alpha = 0.90f)
}

@Composable
fun Color.slightlyWeaken(): Color {
    return copy(alpha = 0.618f)
}

@Composable
fun Color.weaken(): Color {
    return copy(alpha = 0.5f)
}

@Composable
fun Color.stronglyWeaken(): Color {
    return copy(alpha = 1 - 0.618f)
}

@Composable
fun Color.disabledWeaken(): Color {
    return copy(alpha = 0.12f)
}

