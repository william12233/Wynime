@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.wynime.app.ui.foundation.layout

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.animation.NavigationMotionScheme
import com.wynime.app.ui.foundation.animation.StandardAccelerateEasing
import com.wynime.app.ui.foundation.theme.EasingDurations

@ExperimentalMaterial3AdaptiveApi
@Composable
fun ThreePaneScaffoldPaneScope.ListDetailAnimatedPane(
    modifier: Modifier = Modifier,
    useSharedTransition: Boolean = false,
    content: (@Composable AnimatedVisibilityScope.() -> Unit),
) {
    val navMotionScheme by rememberUpdatedState(NavigationMotionScheme.current)
    val enterTransition by remember(useSharedTransition) {
        derivedStateOf {
            when {
                useSharedTransition -> {
                    fadeIn() + expandVertically()
                }

                paneRole == ListDetailPaneScaffoldRole.List -> {
                    navMotionScheme.popEnterTransition
                }

                paneRole == ListDetailPaneScaffoldRole.Detail -> {
                    navMotionScheme.enterTransition
                }

                else -> {
                    fadeIn(
                        tween(
                            EasingDurations.standardAccelerate,
                            delayMillis = EasingDurations.standardDecelerate,
                            easing = StandardAccelerateEasing,
                        ),
                    )
                }
            }
        }
    }
    val wynimeMotionScheme = LocalWynimeMotionScheme.current
    val exitTransition by remember(useSharedTransition, wynimeMotionScheme) {
        derivedStateOf {
            when {
                useSharedTransition -> {
                    fadeOut() + shrinkVertically()
                }

                paneRole == ListDetailPaneScaffoldRole.List -> {
                    navMotionScheme.exitTransition
                }

                paneRole == ListDetailPaneScaffoldRole.Detail -> {
                    navMotionScheme.popExitTransition
                }

                else -> {
                    fadeOut(wynimeMotionScheme.feedItemFadeOutSpec)
                }
            }
        }
    }
    return AnimatedPane(modifier, enterTransition, exitTransition, content = content)
}
