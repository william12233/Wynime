package com.wynime.app.ui.subject.episode

import com.wynime.app.platform.Context
import org.openani.mediamp.MediampPlayer

internal actual suspend fun takeAndroidPlayerScreenshot(
    context: Context,
    player: MediampPlayer,
    filename: String,
): Boolean = false
