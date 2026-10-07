package com.wynime.app.ui.adaptive

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.ListDetailAnimatedPane
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.navigation.BackHandler

@Composable
fun <T> WynimeListDetailPaneScaffold(
    navigator: ThreePaneScaffoldNavigator<T>,
    listPaneTopAppBar: @Composable (PaneScope.() -> Unit)? = null,
    listPaneContent: @Composable (PaneScope.() -> Unit),
    detailPane: @Composable (PaneScope.() -> Unit),
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = ListDetailPaneScaffoldDefaults.windowInsets,
    useSharedTransition: Boolean = false,
    listPanePreferredWidth: Dp = calculateMinimumPaneWidth(),
    minListPaneWidth: Dp = calculateMinimumPaneWidth(),
    minDetailPaneWidth: Dp = minListPaneWidth,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = { state ->
        val interactionSource = remember { MutableInteractionSource() }
        VerticalDragHandle(
            modifier =
                Modifier.paneExpansionDraggable(
                    state,
                    LocalMinimumInteractiveComponentSize.current,
                    interactionSource,
                    state.defaultDragHandleSemantics(),
                ),
            interactionSource = interactionSource,
        )
    },
    scaffoldValue: ThreePaneScaffoldValue = navigator.scaffoldValue,
    layoutParameters: ListDetailLayoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective),
) {
    val coroutineScope = rememberCoroutineScope()
    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            navigator.navigateBack()
        }
    }
    val layoutParametersState by rememberUpdatedState(layoutParameters)
    val contentWindowInsetsState by rememberUpdatedState(contentWindowInsets)
    val scaffoldValueState by rememberUpdatedState(scaffoldValue)

    ListDetailPaneScaffold(
        navigator.scaffoldDirective,
        scaffoldValue,
        listPane = {
            val threePaneScaffoldScope = this
            ListDetailAnimatedPane(
                Modifier
                    .requiredWidthIn(min = minListPaneWidth)
                    .preferredWidth(listPanePreferredWidth),
                useSharedTransition,
            ) {
                Column {
                    val scope =
                        remember(threePaneScaffoldScope, this@ListDetailAnimatedPane) {
                            object : PaneScope {
                                override val listDetailLayoutParameters: ListDetailLayoutParameters
                                    get() = layoutParametersState

                                override val isSinglePane: Boolean
                                    get() = scaffoldValueState.isSinglePane

                                override val paneContentWindowInsets: WindowInsets
                                    get() = when {
                                        isSinglePane -> contentWindowInsetsState
                                        else -> contentWindowInsetsState.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                                    }

                                override fun Modifier.paneContentPadding(
                                    extraStart: Dp,
                                    extraEnd: Dp,
                                ): Modifier {
                                    val endPadding = if (isSinglePane) {
                                        layoutParametersState.listPaneContentEndPadding
                                    } else {
                                        0.dp
                                    }
                                    return Modifier
                                        .padding(
                                            PaddingValues(
                                                start = (layoutParametersState.listPaneContentStartPadding + extraStart)
                                                    .coerceAtLeast(0.dp),
                                                end = (endPadding + extraEnd)
                                                    .coerceAtLeast(0.dp),
                                            ),
                                        )
                                        .consumeWindowInsets(
                                            PaddingValues(
                                                start = (layoutParametersState.listPaneContentStartPadding + extraStart)
                                                    .coerceAtLeast(layoutParametersState.listPaneContentStartPadding),
                                                end = (endPadding + extraEnd)
                                                    .coerceAtLeast(endPadding),
                                            ),
                                        )
                                }
                            }
                        }

                    val decoratedPaneContent = @Composable {
                        Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 1300.dp)) {
                            listPaneContent(scope)
                        }
                    }

                    if (listPaneTopAppBar == null) {
                        decoratedPaneContent()
                    } else {
                        listPaneTopAppBar(scope)
                        Column(
                            Modifier.consumeWindowInsets(
                                contentWindowInsets.only(WindowInsetsSides.Top),
                            ),
                        ) {
                            decoratedPaneContent()
                        }
                    }

                }
            }
        },
        detailPane = {
            val threePaneScaffoldScope = this
            ListDetailAnimatedPane(
                Modifier.requiredWidthIn(min = minDetailPaneWidth),
                useSharedTransition = useSharedTransition,
            ) {
                Card(
                    shape = layoutParameters.detailPaneShape,
                    colors = layoutParameters.detailPaneColors,
                ) {
                    val scope =
                        remember(threePaneScaffoldScope, this@ListDetailAnimatedPane) {
                            object : PaneScope {
                                override val listDetailLayoutParameters: ListDetailLayoutParameters
                                    get() = layoutParametersState

                                override val isSinglePane: Boolean
                                    get() = scaffoldValueState.isSinglePane

                                override val paneContentWindowInsets: WindowInsets
                                    get() = when {
                                        isSinglePane -> contentWindowInsetsState
                                        else -> contentWindowInsetsState.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
                                    }

                                override fun Modifier.paneContentPadding(
                                    extraStart: Dp,
                                    extraEnd: Dp,
                                ): Modifier {
                                    return Modifier
                                        .padding(
                                            PaddingValues(
                                                start = (layoutParametersState.detailPaneContentStartPadding + extraStart)
                                                    .coerceAtLeast(0.dp),
                                                end = (layoutParametersState.detailPaneContentEndPadding + extraEnd)
                                                    .coerceAtLeast(0.dp),
                                            ),
                                        )
                                        .consumeWindowInsets(
                                            PaddingValues(
                                                start = (layoutParametersState.detailPaneContentStartPadding + extraStart)
                                                    .coerceAtLeast(layoutParametersState.detailPaneContentStartPadding),
                                                end = (layoutParametersState.detailPaneContentEndPadding + extraEnd)
                                                    .coerceAtLeast(layoutParametersState.detailPaneContentEndPadding),
                                            ),
                                        )
                                }
                            }
                        }
                    detailPane(scope)
                }
            }
        },
        modifier,

        paneExpansionState = if (layoutParameters.preferSinglePane) {
            null
        } else {
            rememberPaneExpansionState(
                keyProvider = scaffoldValue,
                anchors = calculatePaneAnchors(minListPaneWidth, listPanePreferredWidth, minDetailPaneWidth),
            )
        },
        paneExpansionDragHandle = if (layoutParameters.preferSinglePane) null else paneExpansionDragHandle,
    )
}

@Composable
private fun calculateMinimumPaneWidth(
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
): Dp {
    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(1200) -> 412.dp
        windowSizeClass.isWidthAtLeastBreakpoint(840) -> 360.dp
        else -> {

            (((windowSizeClass.minWidthDp - 24 * 3).toFloat() / 2).dp).coerceAtLeast(360.dp)
        }
    }
}

@Stable
interface PaneScope {

    @Stable
    val listDetailLayoutParameters: ListDetailLayoutParameters

    @Stable
    val isSinglePane get() = listDetailLayoutParameters.preferSinglePane

    @Stable
    val paneContentWindowInsets: WindowInsets

    @Stable
    fun Modifier.paneWindowInsetsPadding(): Modifier = windowInsetsPadding(paneContentWindowInsets)

    @Stable
    fun Modifier.paneContentPadding(
        extraStart: Dp = 0.dp,
        extraEnd: Dp = 0.dp,
    ): Modifier
}

@Immutable
data class ListDetailLayoutParameters(

    val listPaneContentStartPadding: Dp,

    val listPaneContentEndPadding: Dp,

    val detailPaneContentStartPadding: Dp,

    val detailPaneContentEndPadding: Dp,

    val detailPaneShape: Shape,
    val detailPaneColors: CardColors,

    val preferSinglePane: Boolean,

    val highlightSelectedItem: Boolean = !preferSinglePane,
) {
    companion object {
        @Composable
        fun calculate(directive: PaneScaffoldDirective): ListDetailLayoutParameters {
            val isTwoPane = directive.maxHorizontalPartitions > 1
            val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
            return if (isTwoPane) {
                ListDetailLayoutParameters(
                    listPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    listPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneShape = MaterialTheme.shapes.extraLarge.copy(
                        topEnd = ZeroCornerSize,
                        bottomEnd = ZeroCornerSize,
                    ),
                    detailPaneColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    preferSinglePane = false,
                )
            } else {
                ListDetailLayoutParameters(
                    listPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    listPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneShape = RectangleShape,
                    detailPaneColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    preferSinglePane = true,
                )
            }
        }
    }
}

@Suppress("UnusedReceiverParameter")
val ListDetailPaneScaffoldDefaults.windowInsets
    @Composable
    get() = WynimeWindowInsets.forPageContent()

private val ThreePaneScaffoldValue.isSinglePane: Boolean
    get() {
        var count = 0
        if (this[ThreePaneScaffoldRole.Primary] == PaneAdaptedValue.Expanded) count++
        if (this[ThreePaneScaffoldRole.Secondary] == PaneAdaptedValue.Expanded) count++
        if (this[ThreePaneScaffoldRole.Tertiary] == PaneAdaptedValue.Expanded) count++
        return count <= 1
    }

@Composable
private fun calculatePaneAnchors(
    minListPaneWidth: Dp = Dp.Unspecified,
    preferredListPaneWidth: Dp = Dp.Unspecified,
    minDetailPaneWidth: Dp = Dp.Unspecified,
    stepDp: Dp = 32.dp
): List<PaneExpansionAnchor> {
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current

    return remember(density, screenWidthPx, minListPaneWidth, preferredListPaneWidth, minDetailPaneWidth, stepDp) {
        if (screenWidthPx <= 0) return@remember emptyList()

        val minRatio = if (minListPaneWidth != Dp.Unspecified) {
            val px = with(density) { minListPaneWidth.roundToPx() }
            (px / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val maxRatio = if (minDetailPaneWidth != Dp.Unspecified) {
            val px = with(density) { minDetailPaneWidth.roundToPx() }
            ((screenWidthPx - px) / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        } else 1f

        val ratios = mutableListOf<Float>()

        val stepPx = with(density) { stepDp.roundToPx() }
        if (stepPx > 0) {
            val count = (screenWidthPx / stepPx).coerceAtLeast(1)
            for (i in 1..count) {
                val p = (i * stepPx / screenWidthPx.toFloat()).coerceIn(0f, 1f)
                ratios += p
            }
        }

        if (minListPaneWidth != Dp.Unspecified) {
            val px = with(density) { minListPaneWidth.roundToPx() }
            ratios += (px / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        }

        if (preferredListPaneWidth != Dp.Unspecified) {
            val px = with(density) { preferredListPaneWidth.roundToPx() }
            ratios += (px / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        }

        if (minDetailPaneWidth != Dp.Unspecified) {
            val px = with(density) { minDetailPaneWidth.roundToPx() }
            ratios += ((screenWidthPx - px) / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        }

        ratios
            .distinct()
            .filter { it in minRatio..maxRatio }
            .sorted()
            .map { PaneExpansionAnchor.Proportion(it) }
    }
}
