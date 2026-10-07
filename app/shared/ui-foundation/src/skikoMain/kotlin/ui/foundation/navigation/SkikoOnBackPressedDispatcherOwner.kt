package com.wynime.app.ui.foundation.navigation

import androidx.lifecycle.LifecycleOwner
import com.wynime.app.navigation.WynimeNavigator

class SkikoOnBackPressedDispatcherOwner(
    override val onBackPressedDispatcher: OnBackPressedDispatcher,
    lifecycleOwner: LifecycleOwner,
) : OnBackPressedDispatcherOwner, LifecycleOwner by lifecycleOwner {
    constructor(wynimeNavigator: WynimeNavigator, lifecycleOwner: LifecycleOwner) : this(
        popBackStackDispatcher(wynimeNavigator),
        lifecycleOwner,
    )
}

private fun popBackStackDispatcher(wynimeNavigator: WynimeNavigator): OnBackPressedDispatcher =
    OnBackPressedDispatcher(fallback = { wynimeNavigator.popBackStack() })
