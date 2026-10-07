package com.wynime.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

@Composable
actual fun Modifier.blurEffect(
    radius: Dp,
    edgeTreatment: BlurredEdgeTreatment
): Modifier = this.blur(radius, edgeTreatment)