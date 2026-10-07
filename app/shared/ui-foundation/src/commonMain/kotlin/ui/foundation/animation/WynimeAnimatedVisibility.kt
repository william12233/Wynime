package com.wynime.app.ui.foundation.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WynimeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = LocalWynimeMotionScheme.current.animatedVisibility.standardEnter,
    exit: ExitTransition = LocalWynimeMotionScheme.current.animatedVisibility.standardExit,
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}

@Composable
fun RowScope.WynimeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = LocalWynimeMotionScheme.current.animatedVisibility.rowEnter,
    exit: ExitTransition = LocalWynimeMotionScheme.current.animatedVisibility.rowExit,
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}

@Composable
fun ColumnScope.WynimeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = LocalWynimeMotionScheme.current.animatedVisibility.columnEnter,
    exit: ExitTransition = LocalWynimeMotionScheme.current.animatedVisibility.columnExit,
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(visible, modifier, enter, exit, label = label, content = content)
}
