@file:Suppress("ERROR_SUPPRESSION")

package com.wynime.app.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor

@Composable
fun FilledTonalCombinedClickButton(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    indication: Indication = LocalIndication.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) =
    CombinedClickButton(
        onClick = onClick,
        onClickLabel = onClickLabel,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        onDoubleClick = onDoubleClick,
        indication = indication,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Composable
fun CombinedClickButton(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onDoubleClick: (() -> Unit)? = null,
    indication: Indication = LocalIndication.current,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val containerColor = colors.containerColor(enabled)
    val contentColor = colors.contentColor(enabled)
    val shadowElevation = elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp
    Surface(
        modifier = modifier.semantics { role = Role.Button }.combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            role = Role.Button,
            enabled = enabled,
            onClick = onClick,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
        ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.labelLarge,
            contentColor,
        ) {
            Box {
                LongClickProgressFill(
                    interactionSource = interactionSource,
                    enabled = enabled && onLongClick != null,
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.matchParentSize(),
                )
                Row(
                    Modifier.defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight,
                    )
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

@Composable
fun LongClickProgressFill(
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
) {
    val progress by animateLongClickProgressAsState(interactionSource, enabled)
    Box(
        modifier.drawBehind {
            if (progress > 0f) {
                drawRect(
                    color = color,
                    size = Size(width = size.width * progress, height = size.height),
                )
            }
        },
    )
}

@Composable
private fun animateLongClickProgressAsState(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
): State<Float> {
    val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
    val progress = remember { Animatable(0f) }
    val progressState = remember { mutableStateOf(0f) }

    suspend fun animatePressProgress() {
        progress.snapTo(0f)
        progressState.value = 0f

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = longPressTimeoutMillis, easing = LinearEasing),
        ) {
            progressState.value = value
        }
        progressState.value = progress.value
    }

    suspend fun resetProgress() {
        progress.snapTo(0f)
        progressState.value = 0f
    }

    LaunchedEffect(interactionSource, enabled, longPressTimeoutMillis) {
        if (!enabled) {
            resetProgress()
            return@LaunchedEffect
        }

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> animatePressProgress()
                is PressInteraction.Release -> resetProgress()
                is PressInteraction.Cancel -> resetProgress()
            }
        }
    }

    return progressState
}
