package com.wynime.app.ui.foundation.interaction

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent

actual inline fun Modifier.onEnterKeyEvent(crossinline action: (KeyEvent) -> Boolean): Modifier =
    this.onKeyEvent {
        if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
            action(it)
        } else false
    }