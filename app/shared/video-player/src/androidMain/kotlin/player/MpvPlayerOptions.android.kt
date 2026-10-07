package com.wynime.app.videoplayer.player

import org.openani.mediamp.MediampPlayer

actual fun MediampPlayer.isMpv(): Boolean {
    return false
}

actual fun MediampPlayer.applyMpvOptions(options: Map<String, String>) {

}