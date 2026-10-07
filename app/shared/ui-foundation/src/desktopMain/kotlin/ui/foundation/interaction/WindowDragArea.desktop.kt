package com.wynime.app.ui.foundation.interaction

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.always_on_top
import com.wynime.app.ui.lang.always_on_top_disable
import com.wynime.utils.platform.Platform
import org.jetbrains.compose.resources.stringResource

@Composable
actual inline fun WindowDragArea(
    modifier: Modifier,
    crossinline content: @Composable () -> Unit
) {
    val platformWindow = LocalPlatformWindow.current
    val alwaysOnTopText = stringResource(Lang.always_on_top)
    val disableAlwaysOnTopText = stringResource(Lang.always_on_top_disable)

    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(
                    if (platformWindow.isAlwaysOnTop) disableAlwaysOnTopText else alwaysOnTopText,
                ) {
                    platformWindow.setAlwaysOnTop(!platformWindow.isAlwaysOnTop)
                },
            )
        },
    ) {
        if (LocalPlatform.current is Platform.Windows) {
            Box(modifier) {
                content()
            }
        } else {
            val windowScope = platformWindow.windowScope
            windowScope?.run {
                WindowDraggableArea(modifier) {
                    content()
                }
            } ?: content()
        }
    }
}
