package com.wynime.app.ui.foundation.effects

import androidx.compose.ui.Modifier

expect fun Modifier.cursorVisibility(visible: Boolean = true): Modifier

const val TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE = "CursorVisibilityEffect-visible"
const val TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE = "CursorVisibilityEffect-invisible"
