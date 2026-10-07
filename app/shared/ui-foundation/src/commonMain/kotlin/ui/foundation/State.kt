package com.wynime.app.ui.foundation

import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.StateFactoryMarker

private class ImmutableState<T>(
    override val value: T
) : State<T>

@StateFactoryMarker
fun <T> stateOf(value: T): State<T> = ImmutableState(value)
