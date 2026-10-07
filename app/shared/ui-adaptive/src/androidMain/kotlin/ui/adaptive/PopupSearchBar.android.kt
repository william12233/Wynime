package com.wynime.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal actual fun getScreenHeight(): Dp {
    return LocalConfiguration.current.screenHeightDp.dp
}