package com.wynime.app.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.text.ProvideContentColor

@Composable
fun OutlinedTag(
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    label: @Composable RowScope.() -> Unit,
) {
    Tag(
        modifier,
        leadingIcon,
        trailingIcon,
        shape,
        containerColor,
        contentColor,
        border,
        label,
    )
}

@Composable
fun Tag(
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    label: @Composable RowScope.() -> Unit,
) {

    Surface(
        modifier
            .requiredHeight(32.dp)
            .border(border, shape)
            .clip(shape),
        color = containerColor,
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideContentColor(contentColor) {
                leadingIcon?.let {
                    Box(Modifier.size(18.dp)) {
                        it()
                    }
                }

                Row(Modifier.padding(horizontal = 8.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        label()
                    }
                }

                trailingIcon?.let {
                    Box(Modifier.size(18.dp)) {
                        it()
                    }
                }
            }
        }
    }
}
