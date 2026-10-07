package com.wynime.app.ui.foundation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.util.fastLastOrNull
import androidx.lifecycle.LifecycleOwner

actual object LocalOnBackPressedDispatcherOwner {
    private val LocalOnBackPressedDispatcherOwner =
        staticCompositionLocalOf<OnBackPressedDispatcherOwner?> { null }

    actual val current: OnBackPressedDispatcherOwner?
        @Composable
        get() = LocalOnBackPressedDispatcherOwner.current

    actual infix fun provides(dispatcherOwner: OnBackPressedDispatcherOwner):
            ProvidedValue<OnBackPressedDispatcherOwner?> {
        return LocalOnBackPressedDispatcherOwner.provides(dispatcherOwner)
    }
}

actual interface OnBackPressedDispatcherOwner : LifecycleOwner {
    actual val onBackPressedDispatcher: OnBackPressedDispatcher
}

actual class OnBackPressedDispatcher(
    private val fallback: () -> Unit,
) {
    private val handlers = mutableListOf<OnBackPressedHandler>()
    actual fun onBackPressed() {
        handlers.fastLastOrNull { it.enabled }?.onBack() ?: fallback()
    }

    fun registerHandler(handler: OnBackPressedHandler) {
        this.handlers.add(handler)
    }

    fun unregisterHandler(handler: OnBackPressedHandler) {
        this.handlers.remove(handler)
    }
}

interface OnBackPressedHandler {
    val enabled: Boolean
    fun onBack()
}
