package com.wynime.app.videoplayer.player

import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.MpvMediampPlayerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class WynimeMpvMediampPlayerFactory(
    private val delegate: MpvMediampPlayerFactory = MpvMediampPlayerFactory(),
) : MediampPlayerFactory<MpvMediampPlayer> {
    override val forClass: KClass<MpvMediampPlayer> get() = MpvMediampPlayer::class

    override fun create(context: Any, parentCoroutineContext: CoroutineContext): MpvMediampPlayer {
        val player = delegate.create(context, parentCoroutineContext) { handle: MPVHandle ->
            for ((key, value) in mpvStartupOptions()) {
                handle.option(key, value)
            }
        }
        return player
    }
}

internal fun mpvStartupOptions(policy: PlayerBufferPolicy = PlayerBufferPolicy): Map<String, String> = linkedMapOf(
    "cache-secs" to policy.forward.inWholeSeconds.toString(),
    "demuxer-max-bytes" to PlayerBufferPolicy.MPV_MAX_BYTES_PER_DIRECTION.toString(),
    "demuxer-max-back-bytes" to PlayerBufferPolicy.MPV_MAX_BYTES_PER_DIRECTION.toString(),
    "video-sync" to "audio",
    "framedrop" to "vo",
    "hr-seek-framedrop" to "no",
    "hr-seek-demuxer-offset" to "2",
)

internal fun mpvPlaybackSafetyOptions(): Map<String, String> = linkedMapOf(
    "video-sync" to "audio",
    "framedrop" to "vo",
    "hr-seek-framedrop" to "no",
    "hr-seek-demuxer-offset" to "2",
)
