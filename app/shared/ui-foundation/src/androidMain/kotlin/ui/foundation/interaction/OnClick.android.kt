package com.wynime.app.ui.foundation.interaction

import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

actual fun Modifier.onClickEx(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean,
    onDoubleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
)

actual fun Modifier.onRightClickIfSupported(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = this
