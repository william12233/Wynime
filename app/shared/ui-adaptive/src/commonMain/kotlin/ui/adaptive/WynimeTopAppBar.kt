package com.wynime.app.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.interaction.WindowDragArea
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.foundation.layout.Zero
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthAtLeastExpanded
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.paddingIfNotEmpty
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.rememberCurrentTopAppBarContainerColor
import com.wynime.app.ui.foundation.session.SelfAvatar
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.appChromeFrostedGlass
import com.wynime.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.user.TestSelfInfoUiState
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.isDesktop

@Composable
fun WynimeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    avatar: (@Composable (recommendedSize: DpSize) -> Unit)? = null,
    searchIconButton: @Composable (() -> Unit)? = null,
    searchBar: @Composable (() -> Unit)? = null,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    colors: TopAppBarColors = WynimeThemeDefaults.topAppBarColors(),
    windowInsets: WindowInsets = WynimeWindowInsets.forTopAppBar()
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    size: TopAppBarSize = TopAppBarSize.SMALL,

    enableFrostedGlass: Boolean = true,
) {
    val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
    val frostedGlassActive = enableFrostedGlass && isAppChromeFrostedGlassActive()
    val containerColor by rememberCurrentTopAppBarContainerColor(colors, scrollBehavior)
    val appBarModifier = modifier.appChromeFrostedGlass(
        enabled = frostedGlassActive,
        containerColor = containerColor,
    )
    val appBarColors = if (frostedGlassActive) {
        colors.copy(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
    } else {
        colors
    }

    val platformWindow = LocalPlatformWindow.current
    WindowDragArea(
        Modifier.ifThen(LocalPlatform.current.isDesktop()) {

            combinedClickable(
                onDoubleClick = {
                    if (platformWindow.isExactlyMaximized) {
                        platformWindow.floating()
                    } else {
                        platformWindow.maximize()
                    }
                },
                interactionSource = null,
                indication = null,
            ) {}
        },
    ) {
        val additionalPadding =
            if (windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightAtLeastMedium) {
                8.dp
            } else {
                0.dp
            }
        val actionsDecorated: @Composable RowScope.() -> Unit = {
            val horizontalPadding =
                windowSizeClass.paneHorizontalPadding

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveSearchBarLayout(
                    windowSizeClass,
                    searchIconButton,
                    Modifier.weight(1f, fill = false),
                    searchBar,
                )
                actions()
            }

            if (avatar != null) {
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .padding(end = additionalPadding)
                        .paddingIfNotEmpty(
                            start = horizontalPadding,
                            end = (horizontalPadding - 4.dp - additionalPadding).coerceAtLeast(0.dp),
                        ),
                ) {
                    val minSize =
                        if (windowSizeClass.isWidthAtLeastMedium
                            && windowSizeClass.isHeightAtLeastMedium
                        ) {
                            40.dp
                        } else {
                            36.dp
                        }
                    Box(
                        Modifier.sizeIn(
                            minWidth = minSize, maxWidth = 128.dp,
                            minHeight = minSize, maxHeight = minSize,
                        ),
                    ) {
                        avatar(DpSize(minSize, minSize))
                    }
                }
            }
        }

        when (size) {
            TopAppBarSize.SMALL -> {
                TopAppBar(
                    title = {
                        Row(Modifier.padding(start = additionalPadding).padding(vertical = additionalPadding)) {
                            title()
                        }
                    },
                    modifier = appBarModifier,
                    navigationIcon = navigationIcon,
                    actions = actionsDecorated,
                    expandedHeight = expandedHeight,
                    windowInsets = windowInsets,
                    colors = appBarColors,
                    scrollBehavior = scrollBehavior,
                )
            }

            TopAppBarSize.MEDIUM -> {
                MediumTopAppBar(
                    title = {
                        Row(Modifier.padding(start = additionalPadding).padding(vertical = additionalPadding)) {
                            title()
                        }
                    },
                    modifier = appBarModifier,
                    navigationIcon = navigationIcon,
                    actions = actionsDecorated,
                    collapsedHeight = expandedHeight,
                    windowInsets = windowInsets,
                    colors = appBarColors,
                    scrollBehavior = scrollBehavior,
                )
            }

            TopAppBarSize.LARGE -> {
                LargeTopAppBar(
                    title = {
                        Row(Modifier.padding(start = additionalPadding).padding(vertical = additionalPadding)) {
                            title()
                        }
                    },
                    modifier = appBarModifier,
                    navigationIcon = navigationIcon,
                    actions = actionsDecorated,
                    collapsedHeight = expandedHeight,
                    windowInsets = windowInsets,
                    colors = appBarColors,
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    }
}

@Immutable
enum class TopAppBarSize {
    SMALL,
    MEDIUM,
    LARGE
}

@Stable
object WynimeTopAppBarDefaults {
    @Composable
    fun Title(text: String) {
        Text(text, Modifier.width(IntrinsicSize.Max), softWrap = false, maxLines = 1)
    }
}

@Composable
private fun AdaptiveSearchBarLayout(
    windowSizeClass: WindowSizeClass,
    searchIconButton: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    searchBar: @Composable (() -> Unit)?,
) {
    BoxWithConstraints(modifier) {
        AnimatedContent(
            calculateSearchBarSize(windowSizeClass, maxWidth),
            Modifier.animateContentSize(),
            transitionSpec = { expandHorizontally(snap()) togetherWith shrinkHorizontally(snap()) },
            contentAlignment = Alignment.CenterEnd,
        ) { size ->
            when (size) {
                SearchBarSize.ICON_BUTTON -> if (searchIconButton != null) {
                    searchIconButton()
                }

                SearchBarSize.MEDIUM ->
                    if (searchBar != null) {
                        Box(
                            Modifier.sizeIn(minWidth = 240.dp, maxWidth = 360.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            searchBar()
                        }
                    }

                SearchBarSize.EXPANDED ->
                    if (searchBar != null) {
                        Box(
                            Modifier.sizeIn(minWidth = 360.dp, maxWidth = 480.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {

                            searchBar()
                        }
                    }
            }
        }
    }
}

private enum class SearchBarSize {
    ICON_BUTTON,
    MEDIUM,
    EXPANDED
}

private fun calculateSearchBarSize(
    windowSizeClass: WindowSizeClass,
    maxWidth: Dp,
): SearchBarSize {
    return when {
        windowSizeClass.isWidthAtLeastExpanded && maxWidth >= 360.dp
            -> SearchBarSize.EXPANDED

        windowSizeClass.isWidthAtLeastMedium && maxWidth >= 240.dp -> SearchBarSize.MEDIUM
        else -> SearchBarSize.ICON_BUTTON
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewScreenSizes
private fun PreviewWynimeTopAppBar() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    WynimeTopAppBar(
        title = { Text("MyTitle") },
        navigationIcon = { BackNavigationIconButton({}) },
        actions = {
            IconButton({}) {
                Icon(Icons.Rounded.Settings, null)
            }
        },
        avatar = { recommendedSize ->
            SelfAvatar(
                TestSelfInfoUiState,
                size = recommendedSize,
                onClick = { },
            )
        },
        searchIconButton = {
            IconButton({}) {
                Icon(Icons.Rounded.Search, null)
            }
        },
        searchBar = {
            IconButton({}) {
                Icon(Icons.Rounded.Search, null)
            }
        },
        windowInsets = WindowInsets.Zero,
    )
}
