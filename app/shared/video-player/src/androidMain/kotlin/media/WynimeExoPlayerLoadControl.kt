package com.wynime.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.wynime.app.videoplayer.player.PlayerBufferPolicy

@OptIn(UnstableApi::class)
internal fun wynimeExoPlayerLoadControl(): LoadControl {
    val forwardMs = PlayerBufferPolicy.forward.inWholeMilliseconds.toInt()
    val backwardMs = PlayerBufferPolicy.backward.inWholeMilliseconds.toInt()
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
                                forwardMs,
                                forwardMs,
                                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                                                     DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setBackBuffer(backwardMs,                                      true)
        .build()
}
