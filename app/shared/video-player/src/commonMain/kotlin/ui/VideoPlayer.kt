package com.wynime.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.openani.mediamp.MediampPlayer

@Composable
expect fun VideoPlayer(
    player: MediampPlayer,
    modifier: Modifier,
)
