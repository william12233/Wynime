package com.wynime.app.ui.subject.episode.video.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.layout.isInLandscapeMode
import com.wynime.utils.platform.isMobile

@Composable
fun rememberStatusBarHeightAsState(
    isSystemInLandscapeMode: Boolean = isInLandscapeMode(),
): State<Dp> {
    if (LocalPlatform.current.isMobile()) {
        val isLandscapeModeUpdated by rememberUpdatedState(isSystemInLandscapeMode)
        var statusBarHeight by rememberSaveable { mutableStateOf(0) }
        val density by rememberUpdatedState(LocalDensity.current)
        if (!isSystemInLandscapeMode) {

            val insets = WindowInsets.displayCutout
            SideEffect {
                statusBarHeight = insets.getTop(density)
            }
        }

        return remember {
            derivedStateOf {
                if (isLandscapeModeUpdated) {
                    with(density) {
                        statusBarHeight.toDp()
                    }
                } else {
                    0.dp
                }
            }
        }
    } else {
        return remember { mutableStateOf(0.dp) }
    }
}
