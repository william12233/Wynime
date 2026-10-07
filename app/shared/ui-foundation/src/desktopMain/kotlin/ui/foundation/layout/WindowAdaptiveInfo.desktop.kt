package com.wynime.app.ui.foundation.layout

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.toSize
import androidx.window.core.layout.WindowSizeClass

@Composable
@Suppress("DEPRECATION")
actual fun currentWindowAdaptiveInfo1(): WindowAdaptiveInfo {
    val backupState = remember { mutableStateOf<WindowAdaptiveInfo?>(null) }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return try {
        val size = with(density) { windowInfo.containerSize.toSize().toDpSize() }
        return WindowAdaptiveInfo(
            WindowSizeClass.compute(size.width.value, size.height.value),
            Posture(),
        )
    } catch (e: IllegalStateException) {

        backupState.value ?: throw e
    }
}
