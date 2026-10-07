package com.wynime.app.ui.foundation.effects

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

@Composable
actual fun Modifier.blurEffect(
    radius: Dp,
    edgeTreatment: BlurredEdgeTreatment
): Modifier = this then if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Modifier.blur(radius, edgeTreatment)
} else {
    Modifier
        .background(MaterialTheme.colorScheme.inverseOnSurface)
        .graphicsLayer(alpha = 0f)
}