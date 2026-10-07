package com.wynime.app.ui.foundation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.wynime.utils.platform.annotations.TestOnly

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val onBackUpdated by rememberUpdatedState(onBack)
    val enabledUpdated = rememberUpdatedState(enabled)
    val owner = LocalOnBackPressedDispatcherOwner.current ?: return
    DisposableEffect(true, owner) {
        val handler = object : OnBackPressedHandler {
            override val enabled: Boolean by enabledUpdated
            override fun onBack() {
                onBackUpdated()
            }
        }
        owner.onBackPressedDispatcher.registerHandler(handler)
        onDispose {
            owner.onBackPressedDispatcher.unregisterHandler(handler)
        }
    }
}

@TestOnly
actual fun OnBackPressedDispatcher(fallbackOnBackPressed: (() -> Unit)?): OnBackPressedDispatcher {
    return OnBackPressedDispatcher(fallback = fallbackOnBackPressed ?: {})
}
