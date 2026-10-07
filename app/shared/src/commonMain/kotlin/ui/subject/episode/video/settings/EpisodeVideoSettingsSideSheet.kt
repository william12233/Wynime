package com.wynime.app.ui.subject.episode.video.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor
import com.wynime.app.videoplayer.ui.LocalVideoScaffoldSheetWindowInsets

@Composable
fun SideSheetLayout(
    title: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    windowInsets: WindowInsets = LocalVideoScaffoldSheetWindowInsets.current,
    navigationButton: @Composable () -> Unit = { },
    closeButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .clickable(
                onClick = onDismissRequest,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {

        val layoutDirection = LocalLayoutDirection.current
        val horizontalInsets = windowInsets.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val endInset = horizontalInsets.calculateEndPadding(layoutDirection)
        val availableWidth = maxWidth - horizontalInsets.calculateStartPadding(layoutDirection) - endInset
        val contentWidth = (availableWidth * 0.28f).coerceIn(300.dp, 400.dp)

        Surface(
            modifier
                .clickable(
                    onClick = { },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                )
                .fillMaxHeight()
                .width(contentWidth + endInset),
            color = containerColor,
        ) {
            Column(Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 12.dp).padding(vertical = 16.dp).weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.outline) {
                            navigationButton()
                        }

                        Row(Modifier.weight(1f)) {
                            ProvideTextStyleContentColor(
                                MaterialTheme.typography.titleLarge,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                title()
                            }
                        }
                    }

                    Box(Modifier.padding(start = 12.dp)) {
                        closeButton()
                    }
                }

                content()
            }
        }
    }
}
