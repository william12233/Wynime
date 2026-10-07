package com.wynime.app.desktop

import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform

internal fun shouldPreparePlayerBeforeJcef(platform: Platform.Desktop): Boolean =
    platform is Platform.Windows

internal suspend fun initializeJcefAndPlayerBackend(
    preparePlayerBeforeJcef: Boolean,
    preparePlayer: suspend () -> Unit,
    initializeJcef: suspend () -> Unit,
) {
    if (preparePlayerBeforeJcef) {
        preparePlayer()
    }

    initializeJcef()

    if (!preparePlayerBeforeJcef) {
        preparePlayer()
    }
}
