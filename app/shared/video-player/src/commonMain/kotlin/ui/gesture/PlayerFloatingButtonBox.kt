package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.theme.slightlyWeaken

@Composable
fun PlayerFloatingButtonBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background.copy(0.05f),
        contentColor = Color.White,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.slightlyWeaken()),
    ) {
        content()
    }
}