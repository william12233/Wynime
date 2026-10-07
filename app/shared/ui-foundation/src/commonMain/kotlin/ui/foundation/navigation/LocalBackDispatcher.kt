package com.wynime.app.ui.foundation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

fun interface BackDispatcher {

    fun onBackPressed()
}

object LocalBackDispatcher {
    @Stable
    private val NoopBackDispatcher = BackDispatcher {}

    val current: BackDispatcher
        @Composable
        get() = LocalOnBackPressedDispatcherOwner.current?.let { owner ->
            BackDispatcher {
                owner.onBackPressedDispatcher.onBackPressed()
            }
        } ?: NoopBackDispatcher
}
