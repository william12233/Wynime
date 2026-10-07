package com.wynime.app.ui.foundation.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.node.ModifierNodeElement
import com.wynime.app.ui.foundation.effects.onPointerEventMultiplatform

fun Modifier.onBackNavigationInput(onBack: () -> Unit): Modifier =
    this.then(BackKeyNavigationElement(onBack))
        .onPointerEventMultiplatform(PointerEventType.Press) { event ->
            if (event.buttons.isBackPressed && event.changes.none { it.isConsumed }) {
                event.changes.forEach { it.consume() }
                onBack()
            }
        }

class BackKeyEventHandler {
    private var escapePressed = false

    fun onKeyEvent(event: KeyEvent, onBack: () -> Unit): Boolean {
        if (event.key != Key.Escape) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                escapePressed = true
                true
            }

            KeyEventType.KeyUp -> {
                if (!escapePressed) return false
                escapePressed = false
                onBack()
                true
            }

            else -> false
        }
    }

    fun reset() {
        escapePressed = false
    }
}

private data class BackKeyNavigationElement(
    val onBack: () -> Unit,
) : ModifierNodeElement<BackKeyNavigationNode>() {
    override fun create() = BackKeyNavigationNode(onBack)

    override fun update(node: BackKeyNavigationNode) {
        node.onBack = onBack
    }
}

private class BackKeyNavigationNode(
    var onBack: () -> Unit,
) : Modifier.Node(), KeyInputModifierNode {
    private val handler = BackKeyEventHandler()

    override fun onKeyEvent(event: KeyEvent): Boolean = handler.onKeyEvent(event, onBack)

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onDetach() {
        handler.reset()
    }
}
