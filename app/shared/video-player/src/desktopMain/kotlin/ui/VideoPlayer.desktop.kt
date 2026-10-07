package com.wynime.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface

@Composable
actual fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier,
) {

    when (player) {
        is MpvMediampPlayer -> MpvMediampPlayerSurface(player, modifier = modifier)
        else -> error("Unsupported desktop MediampPlayer: ${player::class.qualifiedName}")
    }
}
