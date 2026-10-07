package com.wynime.app.ui.adaptive.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import com.wynime.app.ui.foundation.interaction.WindowDragArea
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.Zero
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightCompact
import com.wynime.app.ui.foundation.layout.isWidthAtLeastExpanded
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.LocalAppChromeOverlayInsets
import com.wynime.app.ui.foundation.theme.isAppChromeFrostedGlassActive

@Composable
fun WynimeNavigationSuiteLayout(
    navigationSuite: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        WynimeNavigationSuiteDefaults.calculateLayoutType(currentWindowAdaptiveInfo1()),

    navigationContainerColor: Color = WynimeThemeDefaults.navigationContainerColor,
    navigationContentColor: Color = contentColorFor(WynimeThemeDefaults.navigationContainerColor),
    content: @Composable () -> Unit = {},
) {

    val overlayNavigationBar = isAppChromeFrostedGlassActive() &&
            layoutType == NavigationSuiteType.NavigationBar

    Surface(modifier = modifier, color = navigationContainerColor, contentColor = navigationContentColor) {
        val consumedInsets = when (layoutType) {
            NavigationSuiteType.NavigationBar ->
                WynimeWindowInsets.forNavigationBar().only(WindowInsetsSides.Bottom)

            NavigationSuiteType.NavigationRail ->
                WynimeWindowInsets.forNavigationRail().only(WindowInsetsSides.Start)

            NavigationSuiteType.NavigationDrawer ->
                WynimeWindowInsets.forNavigationDrawer().only(WindowInsetsSides.Start)

            else -> WindowInsets.Zero
        }

        if (overlayNavigationBar) {
            WynimeNavigationBarOverlayLayout(
                navigationSuite = {
                    WindowDragArea {
                        navigationSuite()
                    }
                },
                content = {
                    Box(Modifier.consumeWindowInsets(consumedInsets)) {
                        content()
                    }
                },
            )
        } else {
            NavigationSuiteScaffoldLayout(
                navigationSuite = {
                    WindowDragArea {
                        navigationSuite()
                    }
                },
                layoutType = layoutType,
                content = {
                    Box(Modifier.consumeWindowInsets(consumedInsets)) {
                        content()
                    }
                },
            )
        }
    }
}

@Composable
private fun WynimeNavigationBarOverlayLayout(
    navigationSuite: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val overlayInsets = remember { MutableWindowInsets() }
    CompositionLocalProvider(LocalAppChromeOverlayInsets provides overlayInsets) {
        Layout(
            content = {
                Box(Modifier.layoutId("navigationSuite")) { navigationSuite() }
                Box(Modifier.layoutId("content")) { content() }
            },
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            val navigationPlaceable = measurables.first { it.layoutId == "navigationSuite" }
                .measure(looseConstraints)
            overlayInsets.insets = WindowInsets(bottom = navigationPlaceable.height)

            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val contentPlaceable = measurables.first { it.layoutId == "content" }
                .measure(constraints)

            layout(layoutWidth, layoutHeight) {
                contentPlaceable.place(0, 0)
                navigationPlaceable.place(0, layoutHeight - navigationPlaceable.height)
            }
        }
    }
}

@Stable
object WynimeNavigationSuiteDefaults {
    fun calculateLayoutType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
        return with(adaptiveInfo) {

            if (windowSizeClass.isHeightCompact
                && windowSizeClass.isWidthAtLeastMedium
            ) {
                return NavigationSuiteType.NavigationRail
            }

            if (windowPosture.isTabletop ||
                windowSizeClass.isHeightCompact
            ) {
                NavigationSuiteType.NavigationBar
            } else if (windowSizeClass.isWidthAtLeastExpanded ||
                windowSizeClass.isWidthAtLeastMedium
            ) {
                NavigationSuiteType.NavigationRail
            } else {
                NavigationSuiteType.NavigationBar
            }
        }
    }
}
