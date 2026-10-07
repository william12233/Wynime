package com.wynime.app.ui.foundation.layout

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

val LocalSharedTransitionScopeProvider: ProvidableCompositionLocal<SharedTransitionScopeProvider?> =
    compositionLocalOf { null }

interface SharedTransitionScopeProvider {
    val sharedTransitionScope: SharedTransitionScope
    val animatedVisibilityScope: AnimatedVisibilityScope
}

fun SharedTransitionScopeProvider(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): SharedTransitionScopeProvider {
    return object : SharedTransitionScopeProvider {
        override val sharedTransitionScope: SharedTransitionScope = sharedTransitionScope
        override val animatedVisibilityScope: AnimatedVisibilityScope = animatedVisibilityScope
    }
}

@Composable
fun Modifier.useSharedTransitionScope(
    block: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Modifier
) = composed {
    val sharedTransitionScopeProvider = LocalSharedTransitionScopeProvider.current ?: return@composed this
    sharedTransitionScopeProvider.sharedTransitionScope.block(
        this, sharedTransitionScopeProvider.animatedVisibilityScope,
    )
}