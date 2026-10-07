package com.wynime.app.ui.foundation.interaction

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent

expect inline fun Modifier.onEnterKeyEvent(crossinline action: (KeyEvent) -> Boolean): Modifier