package com.wynime.app.ui.foundation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults

@Composable
fun rememberCurrentTopAppBarContainerColor(
    colors: TopAppBarColors = WynimeThemeDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = TopAppBarDefaults.pinnedScrollBehavior(),
): State<Color> {

    val targetColor by remember(colors, scrollBehavior) {
        derivedStateOf {
            val overlappingFraction = scrollBehavior?.state?.overlappedFraction ?: 0f
            lerp(
                colors.containerColor,
                colors.scrolledContainerColor,
                FastOutLinearInEasing.transform((if (overlappingFraction > 0.01f) 1f else 0f)),
            )
        }
    }

    return animateColorAsState(
        targetColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
}