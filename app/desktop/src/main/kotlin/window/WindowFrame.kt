package com.wynime.app.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatformDesktop

@Composable
fun FrameWindowScope.WindowFrame(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {

            WindowsWindowFrame(
                windowState = windowState,
                onCloseRequest = onCloseRequest,
                content = content,
            )

}