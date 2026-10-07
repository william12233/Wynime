package com.wynime.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.text.ProvideContentColor

@Composable
fun HeroIcon(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = HeroIconDefaults.contentPadding,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(HeroIconDefaults.iconSize)) {
                ProvideContentColor(HeroIconDefaults.iconColor) {
                    content()
                }
            }
        }
    }
}

@Stable
object HeroIconDefaults {
    @Stable
    val iconColor: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    @Stable
    val iconSize: Dp = 96.dp

    val contentPadding = PaddingValues(
        top = 16.dp,
        bottom = 8.dp,
    )
}