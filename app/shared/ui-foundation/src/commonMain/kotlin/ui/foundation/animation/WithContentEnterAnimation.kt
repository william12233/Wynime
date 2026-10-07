package com.wynime.app.ui.foundation.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun WithContentEnterAnimation(
    modifier: Modifier,
    enter: EnterTransition = LocalWynimeMotionScheme.current.animatedVisibility.screenEnter,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    var isContentReady by rememberSaveable {
        mutableStateOf(false)
    }
    SideEffect {
        isContentReady = true
    }

    WynimeAnimatedVisibility(
        isContentReady,
        modifier,

        enter = enter,
        content = content,
    )
}