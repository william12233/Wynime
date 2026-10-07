package com.wynime.app.ui.foundation

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

const val SLIDER_VALUE_STEP = 0.25f

private const val FLOAT_EPSILON = 1e-4f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SteppedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueIndicator: @Composable (Float) -> Unit,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val labelInteractionSource = rememberHoverExitFilteredInteractionSource(interactionSource)
    var displayedValue by remember(valueRange) { mutableStateOf(value.coerceIn(valueRange)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging) displayedValue = value.coerceIn(valueRange)
    }

    Slider(
        value = displayedValue,
        onValueChange = {
            dragging = true
            val quantizedValue = quantizeSliderValue(it, valueRange)
            displayedValue = quantizedValue
            onValueChange(quantizedValue)
        },
        onValueChangeFinished = {
            dragging = false
            onValueChangeFinished(displayedValue)
        },
        valueRange = valueRange,
        steps = sliderStepsInRange(valueRange),
        interactionSource = interactionSource,
        thumb = { sliderState ->
            Label(
                label = {
                    SliderValueIndicator {
                        valueIndicator(displayedValue)
                    }
                },
                interactionSource = labelInteractionSource,
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    sliderState = sliderState,
                    colors = colors,
                )
            }
        },
        colors = colors,
        track = { sliderState ->

            SliderDefaults.Track(
                sliderState = sliderState,
                trackCornerSize = 4.dp,
                colors = colors,
                drawTick = { _, _ -> },
            )
        },
        modifier = modifier,
    )
}

fun quantizeSliderValue(
    rawValue: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    if (!rawValue.isFinite()) return range.start
    return ((rawValue.coerceIn(range) / SLIDER_VALUE_STEP).roundToInt() * SLIDER_VALUE_STEP).coerceIn(range)
}

fun sliderStepsInRange(range: ClosedFloatingPointRange<Float>): Int {
    val intervals = ((range.endInclusive - range.start) / SLIDER_VALUE_STEP + FLOAT_EPSILON).toInt()
    return (intervals - 1).coerceAtLeast(0)
}

@Composable
fun rememberHoverExitFilteredInteractionSource(
    source: MutableInteractionSource,
): MutableInteractionSource {
    val filtered = remember { MutableInteractionSource() }
    LaunchedEffect(source, filtered) {
        var dragging = false
        source.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragging = true
                    filtered.emit(interaction)
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    dragging = false
                    filtered.emit(interaction)
                }
                is HoverInteraction.Exit -> {
                    if (!dragging) filtered.emit(interaction)
                }
                is PressInteraction -> Unit
                else -> filtered.emit(interaction)
            }
        }
    }
    return filtered
}

@Composable
fun TooltipScope.SliderValueIndicator(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp, 44.dp),
        shape = RoundedCornerShape(22.dp),
        color = TooltipDefaults.plainTooltipContainerColor,
        contentColor = TooltipDefaults.plainTooltipContentColor,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content)
        }
    }
}
