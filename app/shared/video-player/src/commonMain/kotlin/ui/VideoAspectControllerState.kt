package com.wynime.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.wynime.app.ui.lang.*
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio
import org.jetbrains.compose.resources.*

@Stable
class VideoAspectRatioControllerState(
    private val videoAspectRatio: VideoAspectRatio,
    scope: CoroutineScope,
) {
    var currentMode by mutableStateOf(videoAspectRatio.mode.value)
    val currentIndex by derivedStateOf { Entries.indexOf(currentMode) }

    init {
        scope.launch {
            videoAspectRatio.mode.collect {
                currentMode = it
            }
        }
    }

    fun setMode(mode: AspectRatioMode) {
        videoAspectRatio.setMode(mode)
    }

    companion object {
        val Entries: List<AspectRatioMode> = AspectRatioMode.entries
    }
}

@Composable
fun renderAspectRatioMode(mode: AspectRatioMode): String {
    return when (mode) {
        AspectRatioMode.FIT -> stringResource(Lang.video_player_aspect_fit)
        AspectRatioMode.STRETCH -> stringResource(Lang.video_player_aspect_stretch)
        AspectRatioMode.CROP -> stringResource(Lang.video_player_aspect_crop)
    }
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpVideoAspectRatio : VideoAspectRatio {
    override val mode: StateFlow<AspectRatioMode> = MutableStateFlow(AspectRatioMode.FIT)
    override fun setMode(mode: AspectRatioMode) {

    }
}
