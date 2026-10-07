@file:Suppress("NOTHING_TO_INLINE")

package com.wynime.app.ui.foundation.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wynime.app.platform.PlatformWindow

fun Modifier.desktopTitleBarPadding(): Modifier = composed({ name = "desktopTitleBarPadding" }) {
    Modifier.windowInsetsPadding(WindowInsets.desktopTitleBar)
}

@Composable
fun WindowInsets.Companion.desktopTitleBar() = desktopTitleBar

val WindowInsets.Companion.desktopTitleBar
    @Composable
    get() = LocalTitleBarInsets.current

val WindowInsets.Companion.desktopCaptionButton
    @Composable
    get() = LocalCaptionButtonInsets.current

val ZeroInsets = WindowInsets(0.dp)

val LocalTitleBarInsets = compositionLocalOf { ZeroInsets }
val LocalCaptionButtonInsets = compositionLocalOf { ZeroInsets }

operator fun WindowInsets.plus(other: WindowInsets): WindowInsets {
    return PlusWindowInsets(this, other) { a, b -> a + b }
}

@Composable
inline fun WindowInsets.isTopRight() = getRight(LocalDensity.current, LocalLayoutDirection.current) > 0

@Immutable
private class PlusWindowInsets(
    private val a: WindowInsets,
    private val b: WindowInsets,
    private val function: (Int, Int) -> Int,
) : WindowInsets {
    override fun getBottom(density: Density): Int {
        return function(a.getBottom(density), b.getBottom(density))
    }

    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int {
        return function(a.getLeft(density, layoutDirection), b.getLeft(density, layoutDirection))
    }

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int {
        return function(a.getRight(density, layoutDirection), b.getRight(density, layoutDirection))
    }

    override fun getTop(density: Density): Int {
        return function(a.getTop(density), b.getTop(density))
    }
}

typealias PlatformWindowMP = PlatformWindow

val LocalPlatformWindow: ProvidableCompositionLocal<PlatformWindowMP> = staticCompositionLocalOf {
    error("No PlatformWindow provided")
}

@Stable
object WynimeWindowInsets {

    val systemBars
        @Composable
        get() = WindowInsets.systemBars + WindowInsets.desktopTitleBar

    val statusBars
        @Composable
        get() = WindowInsets.statusBars + WindowInsets.desktopTitleBar

    val safeDrawing
        @Composable
        get() = WindowInsets.safeDrawing + WindowInsets.desktopTitleBar

    @Composable
    inline fun forTopAppBar() = (systemBars.union(WindowInsets.displayCutout))
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    @Composable
    inline fun forTopAppBarWithoutDesktopTitle() = forTopAppBar()

    @Composable
    inline fun forPageContent() = safeDrawing

    @Composable
    fun forColumnPageContent() = safeDrawing.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
    )

    @Composable
    inline fun forSearchBar() = safeDrawing

    @Composable
    fun forNavigationBar() = safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    @Composable
    fun forNavigationRail() = safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)

    @Composable
    @NonRestartableComposable
    inline fun forNavigationDrawer() = forNavigationRail()

}
