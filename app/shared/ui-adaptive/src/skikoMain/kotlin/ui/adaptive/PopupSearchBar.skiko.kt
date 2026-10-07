package com.wynime.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun getScreenHeight(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return with(density) {
        windowInfo.containerSize.height.toDp()
    }
}