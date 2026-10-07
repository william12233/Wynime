package com.wynime.app.videoplayer.player

import org.openani.mediamp.MediampPlayer

expect fun MediampPlayer.isMpv(): Boolean

expect fun MediampPlayer.applyMpvOptions(options: Map<String, String>)