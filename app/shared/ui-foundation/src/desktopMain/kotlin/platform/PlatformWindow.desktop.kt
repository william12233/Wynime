package com.wynime.app.platform

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.wynime.app.platform.window.BasicWindowProc
import com.wynime.app.platform.window.LayoutHitTestOwner
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.isWindows

actual open class PlatformWindow(
    val windowHandle: Long,
    val windowScope: WindowScope? = null,
    val windowState: WindowState,
    val platform: Platform,
    val layoutHitTestOwner: LayoutHitTestOwner? = null,
    private val alwaysOnTopState: MutableState<Boolean> = mutableStateOf(false),
) {
    internal var savedWindowsWindowState: SavedWindowsWindowState? = null

    internal val windowsWindowProc = MutableStateFlow<BasicWindowProc?>(null)

    val accentColor: Flow<Color>
        get() = windowsWindowProc.flatMapLatest {
            it?.accentColor ?: flowOf(Color.Unspecified)
        }

    private var isWindowsUndecoratedFullscreen by mutableStateOf(false)

    actual val isExactlyMaximized: Boolean get() = windowState.placement == WindowPlacement.Maximized

    actual val isUndecoratedFullscreen: Boolean by derivedStateOf {
        if (platform.isWindows()) {
            isWindowsUndecoratedFullscreen
        } else {
            windowState.placement == WindowPlacement.Fullscreen
        }
    }

    actual val deviceOrientation: DeviceOrientation = DeviceOrientation.LANDSCAPE

    internal fun onWindowsUndecoratedFullscreenStateChange(newState: Boolean) {
        isWindowsUndecoratedFullscreen = newState
    }

    actual fun maximize() {
        windowState.placement = WindowPlacement.Maximized
    }

    actual fun floating() {
        windowState.placement = WindowPlacement.Floating
    }

    actual val isAlwaysOnTop: Boolean get() = alwaysOnTopState.value

    actual fun setAlwaysOnTop(alwaysOnTop: Boolean) {
        alwaysOnTopState.value = alwaysOnTop
    }
}

class SavedWindowsWindowState(
    val style: Int,
    val exStyle: Int,
    val rect: Rect,
    val maximized: Boolean,
)
