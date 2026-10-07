package com.wynime.app.ui.foundation.interaction

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo

expect fun Modifier.onClickEx(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier

expect fun Modifier.onRightClickIfSupported(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier

fun Modifier.onRightClickIfSupported(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "onRightClickIfSupported"
        properties["enabled"] = enabled
        properties["onClick"] = onClick
    },
) {
    this.onRightClickIfSupported(
        interactionSource = remember { MutableInteractionSource() },
        enabled = enabled,
        onClick = onClick,
    )
}

fun Modifier.clickableAndMouseRightClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = clickable(enabled = enabled, onClick = onClick)
    .onRightClickIfSupported(enabled = enabled, onClick = onClick)
