package com.wynime.app.videoplayer.player

import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer

private val logger = logger<MpvMediampPlayer>()

actual fun MediampPlayer.isMpv(): Boolean {
    return this is MpvMediampPlayer
}

actual fun MediampPlayer.applyMpvOptions(options: Map<String, String>) {
    if (options.isEmpty()) return
    if (this !is MpvMediampPlayer) return

    val handle = try {
        impl as? MPVHandle ?: return
    } catch (e: Throwable) {
        logger.error(e) { "Failed to obtain MPVHandle, custom mpv options are not applied" }
        return
    }

    for ((key, value) in options) {
        val applied = try {
            handle.option(key, value)
        } catch (e: Throwable) {
            logger.error(e) { "Failed to apply mpv option '$key'" }
            continue
        }
        if (applied) {
            logger.info { "Applied custom mpv option '$key=$value'" }
        } else {
            logger.warn {
                "mpv rejected custom option '$key=$value'. " +
                        "The option may not exist, its value may be invalid, or it can only be set at startup."
            }
        }
    }
}