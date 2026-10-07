package com.wynime.app.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.theme.slightlyWeaken
import com.wynime.app.videoplayer.ui.gesture.PlayerGestureHost
import com.wynime.app.videoplayer.ui.progress.PlayerControllerBar
import com.wynime.app.videoplayer.ui.top.PlayerTopBar

val LocalVideoScaffoldSheetWindowInsets = compositionLocalOf<WindowInsets> { WindowInsets(0) }

@Composable
fun VideoScaffold(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeContent,
    maintainAspectRatio: Boolean = !expanded,
    controllerState: PlayerControllerState,
    gestureLocked: Boolean = false,
    topBar: @Composable RowScope.() -> Unit = {},

    video: @Composable BoxScope.() -> Unit = {},
    gestureHost: @Composable BoxWithConstraintsScope.() -> Unit = {},
    floatingMessage: @Composable BoxScope.() -> Unit = {},
    rhsButtons: @Composable ColumnScope.() -> Unit = {},
    gestureLock: @Composable ColumnScope.() -> Unit = {},
    bottomBar: @Composable RowScope.() -> Unit = {},
    detachedProgressSlider: @Composable () -> Unit = {},
    floatingBottomEnd: @Composable RowScope.() -> Unit = {},
    rhsSheet: @Composable () -> Unit = {},
    leftBottomTips: @Composable () -> Unit = {},
    centerOverlay: @Composable BoxScope.() -> Unit = {},
    framePreviewOverlay: @Composable BoxScope.() -> Unit = {},
    playerStatsOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val inlineSliderOnly = controllerState.visibility == ControllerVisibility.InlineSliderOnly
    val controllerVisibility = controllerState.visibility
        .withGestureLocked(gestureLocked)
        .withExpanded(expanded)

    val enterTransition = LocalWynimeMotionScheme.current.animatedVisibility.standardEnter
    val exitTransition = LocalWynimeMotionScheme.current.animatedVisibility.standardExit
    BoxWithConstraints(
        modifier.then(if (expanded) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(
                    if (!maintainAspectRatio) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().height(maxWidth * 9 / 16)
                    },
                ),
        ) {
            Box(
                Modifier
                    .background(Color.Transparent)
                    .matchParentSize(),
            ) {
                video()
                Box(Modifier.matchParentSize())
            }

            BoxWithConstraints(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                gestureHost()
            }

            Box(
                Modifier.matchParentSize()
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(12.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                playerStatsOverlay()
            }

            Box(Modifier) {
                Column(Modifier.fillMaxSize().background(Color.Transparent)) {

                    com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility(
                        visible = controllerVisibility.topBar || inlineSliderOnly,
                        enter = enterTransition,
                        exit = exitTransition,
                    ) {
                        Box {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent.copy(0.72f),
                                            0.32f to Color.Transparent.copy(0.45f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                            )
                            val alwaysOnRequester = rememberAlwaysOnRequester(controllerState, "topBar")

                            Column(
                                Modifier
                                    .keepLayoutWhenHidden(inlineSliderOnly)
                                    .hoverToRequestAlwaysOn(alwaysOnRequester)
                                    .fillMaxWidth(),
                            ) {

                                val desktopTitleBarInsets = WindowInsets.desktopTitleBar.only(WindowInsetsSides.Top)
                                Spacer(
                                    modifier = Modifier.fillMaxWidth()
                                        .pointerInput(Unit) {}
                                        .windowInsetsPadding(desktopTitleBarInsets),
                                )
                                Row(
                                    Modifier.fillMaxWidth()
                                        .consumeWindowInsets(desktopTitleBarInsets)
                                        .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                        topBar()
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            Box(
                                Modifier.matchParentSize()
                                    .keepLayoutWhenHidden(inlineSliderOnly)
                                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Top))
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                    centerOverlay()
                                }
                            }
                        }
                    }

                    Box(Modifier.weight(1f, fill = true).fillMaxWidth())

                    Column {

                        WynimeAnimatedVisibility(
                            visible = controllerVisibility.bottomBar,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            val alwaysOnRequester = rememberAlwaysOnRequester(controllerState, "bottomBar")
                            Column(
                                Modifier
                                    .hoverToRequestAlwaysOn(alwaysOnRequester)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val event = awaitPointerEvent()
                                            if (event.changes.all { it.pressed }) {

                                                alwaysOnRequester.request()
                                            }
                                            var releaseEvent = awaitPointerEvent()
                                            while (releaseEvent.changes.any { it.pressed }) {
                                                releaseEvent = awaitPointerEvent()
                                            }
                                            alwaysOnRequester.cancelRequest()
                                        }
                                    }
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            1 - 0.32f to Color.Transparent.copy(0.45f),
                                            1f to Color.Transparent.copy(0.72f),
                                        ),
                                    ),
                            ) {
                                Spacer(Modifier.height(if (expanded) 12.dp else 6.dp))
                                Row(
                                    Modifier.fillMaxWidth()
                                        .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                                        bottomBar()
                                    }
                                }
                            }

                        }
                        WynimeAnimatedVisibility(
                            visible = controllerVisibility.detachedSlider,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                            ) {
                                detachedProgressSlider()
                            }
                        }
                    }
                }
                WynimeAnimatedVisibility(
                    controllerVisibility.floatingBottomEnd && !expanded,
                    Modifier.align(Alignment.BottomEnd),
                    enter = enterTransition,
                    exit = exitTransition,
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.End)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            floatingBottomEnd()
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().background(Color.Transparent)
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.End)),
            ) {
                Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
                    Column(
                        Modifier.padding(end = 16.dp).align(Alignment.CenterEnd),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WynimeAnimatedVisibility(
                            visible = controllerVisibility.rhsBar,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            rhsButtons()
                        }

                        WynimeAnimatedVisibility(
                            visible = controllerVisibility.gestureLock,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            gestureLock()
                        }
                    }
                }
            }

            Box(Modifier.matchParentSize()) {
                Column(Modifier.windowInsetsPadding(contentWindowInsets)) {
                    Box(Modifier.weight(0.5f))
                    Row(
                        Modifier.weight(0.5f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leftBottomTips()
                    }
                }
            }

            Box(
                Modifier.matchParentSize().windowInsetsPadding(contentWindowInsets),
                contentAlignment = Alignment.Center,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground.slightlyWeaken()) {
                        floatingMessage()
                    }
                }
            }

            Box(
                Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                framePreviewOverlay()
            }

            Box(Modifier.matchParentSize()) {
                CompositionLocalProvider(LocalVideoScaffoldSheetWindowInsets provides contentWindowInsets) {
                    rhsSheet()
                }
            }
        }
    }
}

internal fun Modifier.keepLayoutWhenHidden(hidden: Boolean): Modifier {
    if (!hidden) return this
    return alpha(0f)
        .clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
}

@Stable
private fun ControllerVisibility.withGestureLocked(gestureLocked: Boolean): ControllerVisibility {
    return if (gestureLocked) {
        copy(
            topBar = false,
            bottomBar = false,
            detachedSlider = false,
            rhsBar = false,
        )
    } else {
        this
    }
}

@Stable
private fun ControllerVisibility.withExpanded(isExpanded: Boolean): ControllerVisibility {
    return if (isExpanded) {
        copy(floatingBottomEnd = false)
    } else {
        this
    }
}
