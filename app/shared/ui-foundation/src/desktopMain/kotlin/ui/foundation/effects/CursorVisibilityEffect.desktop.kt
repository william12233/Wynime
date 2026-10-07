package com.wynime.app.ui.foundation.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import com.wynime.app.platform.window.AwtWindowUtils.Companion.blankCursor

actual fun Modifier.cursorVisibility(visible: Boolean): Modifier {
    val blank = blankCursor

        ?: return testTag(
            if (visible) TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE else TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE,
        )

    return pointerHoverIcon(if (visible) PointerIcon.Default else PointerIcon(blank))
        .testTag(
            if (visible) TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE else TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE,
        )
}
