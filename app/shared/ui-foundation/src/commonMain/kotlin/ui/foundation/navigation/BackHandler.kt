package com.wynime.app.ui.foundation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.lifecycle.LifecycleOwner
import com.wynime.utils.platform.annotations.TestOnly

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

expect object LocalOnBackPressedDispatcherOwner {
    val current: OnBackPressedDispatcherOwner?
        @Composable get

    infix fun provides(dispatcherOwner: OnBackPressedDispatcherOwner):
            ProvidedValue<OnBackPressedDispatcherOwner?>
}

expect interface OnBackPressedDispatcherOwner : LifecycleOwner {
    val onBackPressedDispatcher: OnBackPressedDispatcher
}

expect class OnBackPressedDispatcher {
    fun onBackPressed()
}

@TestOnly
expect fun OnBackPressedDispatcher(
    fallbackOnBackPressed: (() -> Unit)? = null
): OnBackPressedDispatcher
