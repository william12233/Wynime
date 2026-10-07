package com.wynime.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@Stable
interface PlayerFullscreenState {

    val isFullscreen: Boolean

    fun request(fullscreen: Boolean)
}

fun PlayerFullscreenState.toggle() {
    request(!isFullscreen)
}

@Stable
object NoOpPlayerFullscreenState : PlayerFullscreenState {
    override val isFullscreen: Boolean get() = false
    override fun request(fullscreen: Boolean) {}
}

@Stable
class MutablePlayerFullscreenState(
    initialIsFullscreen: Boolean = false,
) : PlayerFullscreenState {
    override var isFullscreen: Boolean by mutableStateOf(initialIsFullscreen)
        private set

    override fun request(fullscreen: Boolean) {
        isFullscreen = fullscreen
    }
}

@Composable
fun rememberPlayerFullscreenState(
    isFullscreen: () -> Boolean,
    onRequest: (fullscreen: Boolean) -> Unit,
): PlayerFullscreenState {
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnRequest by rememberUpdatedState(onRequest)
    return remember {
        object : PlayerFullscreenState {
            override val isFullscreen: Boolean get() = currentIsFullscreen()

            override fun request(fullscreen: Boolean) {
                if (currentIsFullscreen() == fullscreen) return
                currentOnRequest(fullscreen)
            }
        }
    }
}
