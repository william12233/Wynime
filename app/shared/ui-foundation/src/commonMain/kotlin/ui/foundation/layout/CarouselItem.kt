package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor
import com.wynime.app.ui.foundation.theme.LocalDarkOnSurface
import com.wynime.app.ui.foundation.theme.appColorScheme

@Stable
private val carouselBrush = Brush.verticalGradient(
    listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black.copy(alpha = 0.612f),
    ),
)

@Composable
fun CarouselItemScope.CarouselItem(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    colors: CarouselItemColors = CarouselItemDefaults.colors(),
    shape: Shape = CarouselItemDefaults.shape,
    image: @Composable () -> Unit,
) {
    val maskShape = rememberMaskShape(shape)
    BasicCarouselItem(
        label,
        modifier,
        supportingText,
        overlay,
        colors,
        maskShape,
        image = image,
    )
}

@Composable
fun BasicCarouselItem(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    colors: CarouselItemColors = CarouselItemDefaults.colors(),
    maskShape: Shape = RectangleShape,
    brushLayerModifier: Modifier = Modifier,
    image: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.clip(maskShape)) {
            image()
        }
        Box(brushLayerModifier.matchParentSize().background(carouselBrush, maskShape)) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    colors.titleColor,
                ) {
                    label()
                }
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.labelSmall,
                    colors.supportingTextColor,
                ) {
                    supportingText()
                }
            }
        }
        Box(Modifier.matchParentSize()) {
            overlay()
        }
    }
}

@Immutable
data class CarouselItemColors(
    val titleColor: Color,
    val supportingTextColor: Color,
)

@Stable
object CarouselItemDefaults {
    val shape: Shape
        @Composable
        get() = MaterialTheme.shapes.extraLarge

    @Composable
    fun colors(): CarouselItemColors {
        val provided = LocalDarkOnSurface.current
        val onSurface = if (provided.isSpecified) provided else appColorScheme(isDark = true).onSurface
        return CarouselItemColors(
            titleColor = onSurface,
            supportingTextColor = onSurface,
        )
    }

    @Composable
    fun Text(
        value: String,
        softWrap: Boolean = true,
        maxLines: Int = 1
    ) {
        androidx.compose.material3.Text(
            value,
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }

    @Composable
    fun itemSize(): CarouselItemSize {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        val preferredWidth =
            if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
                300.dp
            } else {
                240.dp
            }
        return CarouselItemSize(
            preferredWidth = preferredWidth,
            imageHeight = 213.dp,
        )
    }
}

@Immutable
data class CarouselItemSize(
    val preferredWidth: Dp,
    val imageHeight: Dp,
)
