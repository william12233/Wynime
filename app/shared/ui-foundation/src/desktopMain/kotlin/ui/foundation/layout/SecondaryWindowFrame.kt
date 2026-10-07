package com.wynime.app.ui.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState

typealias SecondaryWindowFrame = @Composable FrameWindowScope.(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) -> Unit

val LocalSecondaryWindowFrame = staticCompositionLocalOf<SecondaryWindowFrame?> { null }
