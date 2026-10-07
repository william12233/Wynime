@file:Suppress("ConstPropertyName")

package com.wynime.app.ui.foundation.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.ui.foundation.LocalPlatformFontFamily
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.copyWithPlatformFontFamily
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults.pageContentBackgroundColor

val LocalThemeSettings = compositionLocalOf<ThemeSettings> {
    error("LocalThemeSettings not provided")
}

@Composable
expect fun appColorScheme(
    seedColor: Color = LocalThemeSettings.current.seedColor,
    useDynamicTheme: Boolean = LocalThemeSettings.current.useDynamicTheme,
    useBlackBackground: Boolean = LocalThemeSettings.current.useBlackBackground,
    isDark: Boolean = when (LocalThemeSettings.current.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkThemeDetected()
    },
): ColorScheme

@Composable
expect fun isPlatformSupportDynamicTheme(): Boolean

val LocalDarkOnSurface: ProvidableCompositionLocal<Color> = compositionLocalOf { Color.Unspecified }

@Composable
fun WynimeTheme(
    darkModeOverride: DarkMode? = null,
    content: @Composable () -> Unit,
) {
    val platformFontFamily = LocalPlatformFontFamily.current
    val isDark = when (darkModeOverride ?: LocalThemeSettings.current.darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AUTO -> isSystemInDarkThemeDetected()
    }
    val colorScheme = appColorScheme(isDark = isDark)

    val darkOnSurface = if (isDark) colorScheme.onSurface else appColorScheme(isDark = true).onSurface

    CompositionLocalProvider(LocalDarkOnSurface provides darkOnSurface) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography.copyWithPlatformFontFamily(platformFontFamily),
            content = content,
        )
    }
}

@Stable
object WynimeThemeDefaults {

    val navigationContainerColor
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer

    val pageContentBackgroundColor
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest

    @Composable
    fun topAppBarColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        )

    @Composable
    fun transparentAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )

    @Composable
    fun backgroundCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerLow),
    )

    @Composable
    fun primaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh),
    )

    @Stable
    @Deprecated(
        "Use LocalWynimeMotionScheme instead",
        ReplaceWith(
            "LocalWynimeMotionScheme.current.feedItemFadeInSpec",
            "com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val feedItemFadeInSpec: FiniteAnimationSpec<Float>
        @Composable
        get() = LocalWynimeMotionScheme.current.feedItemFadeInSpec

    @Stable
    @Deprecated(
        "Use LocalWynimeMotionScheme instead",
        ReplaceWith(
            "LocalWynimeMotionScheme.current.feedItemPlacementSpec",
            "com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val feedItemPlacementSpec: FiniteAnimationSpec<IntOffset>
        @Composable
        get() = LocalWynimeMotionScheme.current.feedItemPlacementSpec

    @Stable
    @Deprecated(
        "Use LocalWynimeMotionScheme instead",
        ReplaceWith(
            "LocalWynimeMotionScheme.current.feedItemFadeOutSpec",
            "com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val feedItemFadeOutSpec: FiniteAnimationSpec<Float>
        @Composable
        get() = LocalWynimeMotionScheme.current.feedItemFadeOutSpec

    @Stable
    @Deprecated(
        "Use LocalWynimeMotionScheme instead",
        ReplaceWith(
            "LocalWynimeMotionScheme.current.standardAnimatedContentTransition",
            "com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme",
        ),
        level = DeprecationLevel.ERROR,
    )
    val standardAnimatedContentTransition: AnimatedContentTransitionScope<*>.() -> ContentTransform
        @Composable
        get() = LocalWynimeMotionScheme.current.animatedContent.standard
}

@Stable
object EasingDurations {

    const val emphasized = 500
    const val emphasizedDecelerate = 400
    const val emphasizedAccelerate = 200
    const val standard = 300
    const val standardDecelerate = 250
    const val standardAccelerate = 200
}

fun modifyColorSchemeForBlackBackground(
    colorScheme: ColorScheme,
    isDark: Boolean,
    useBlackBackground: Boolean,
): ColorScheme {
    return if (isDark && useBlackBackground) {
        colorScheme.copy(
            background = Color.Black,
            onBackground = Color.White,

            surface = Color.Black,
            onSurface = Color.White,
            surfaceContainerLowest = Color.Black,

            surfaceVariant = Color.Black,
            onSurfaceVariant = Color.White,
        )
    } else colorScheme
}

@Composable
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(EasingDurations.standard),
): ColorScheme {
    return ColorScheme(
        primary = animateColorAsState(targetColorScheme.primary, animationSpec).value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, animationSpec).value,
        primaryContainer = animateColorAsState(targetColorScheme.primaryContainer, animationSpec).value,
        onPrimaryContainer = animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec).value,
        inversePrimary = animateColorAsState(targetColorScheme.inversePrimary, animationSpec).value,
        secondary = animateColorAsState(targetColorScheme.secondary, animationSpec).value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, animationSpec).value,
        secondaryContainer = animateColorAsState(targetColorScheme.secondaryContainer, animationSpec).value,
        onSecondaryContainer = animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec).value,
        tertiary = animateColorAsState(targetColorScheme.tertiary, animationSpec).value,
        onTertiary = animateColorAsState(targetColorScheme.onTertiary, animationSpec).value,
        tertiaryContainer = animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec).value,
        onTertiaryContainer = animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec).value,
        background = animateColorAsState(targetColorScheme.background, animationSpec).value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, animationSpec).value,
        surface = animateColorAsState(targetColorScheme.surface, animationSpec).value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, animationSpec).value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, animationSpec).value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec).value,
        surfaceTint = animateColorAsState(targetColorScheme.surfaceTint, animationSpec).value,
        inverseSurface = animateColorAsState(targetColorScheme.inverseSurface, animationSpec).value,
        inverseOnSurface = animateColorAsState(targetColorScheme.inverseOnSurface, animationSpec).value,
        surfaceBright = animateColorAsState(targetColorScheme.surfaceBright, animationSpec).value,
        surfaceDim = animateColorAsState(targetColorScheme.surfaceDim, animationSpec).value,
        surfaceContainer = animateColorAsState(targetColorScheme.surfaceContainer, animationSpec).value,
        surfaceContainerHigh = animateColorAsState(targetColorScheme.surfaceContainerHigh, animationSpec).value,
        surfaceContainerHighest = animateColorAsState(targetColorScheme.surfaceContainerHighest, animationSpec).value,
        surfaceContainerLow = animateColorAsState(targetColorScheme.surfaceContainerLow, animationSpec).value,
        surfaceContainerLowest = animateColorAsState(targetColorScheme.surfaceContainerLowest, animationSpec).value,
        error = animateColorAsState(targetColorScheme.error, animationSpec).value,
        onError = animateColorAsState(targetColorScheme.onError, animationSpec).value,
        errorContainer = animateColorAsState(targetColorScheme.errorContainer, animationSpec).value,
        onErrorContainer = animateColorAsState(targetColorScheme.onErrorContainer, animationSpec).value,
        outline = animateColorAsState(targetColorScheme.outline, animationSpec).value,
        outlineVariant = animateColorAsState(targetColorScheme.outlineVariant, animationSpec).value,
        scrim = animateColorAsState(targetColorScheme.scrim, animationSpec).value,
    )
}
