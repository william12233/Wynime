@file:OptIn(TestOnly::class)

package com.wynime.app.ui.exploration.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.number
import com.wynime.app.ui.adaptive.WynimeTopAppBar
import com.wynime.app.ui.adaptive.HorizontalScrollControlScaffoldOnDesktop
import com.wynime.app.ui.foundation.HorizontalScrollControlState
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.pagerTabIndicatorOffset
import com.wynime.app.ui.foundation.rememberHorizontalScrollControlState
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_schedule
import com.wynime.app.ui.lang.exploration_schedule_last_weekday
import com.wynime.app.ui.lang.exploration_schedule_next_weekday
import com.wynime.app.ui.lang.exploration_schedule_this_weekday
import com.wynime.app.ui.lang.exploration_schedule_weekday_friday
import com.wynime.app.ui.lang.exploration_schedule_weekday_monday
import com.wynime.app.ui.lang.exploration_schedule_weekday_saturday
import com.wynime.app.ui.lang.exploration_schedule_weekday_sunday
import com.wynime.app.ui.lang.exploration_schedule_weekday_thursday
import com.wynime.app.ui.lang.exploration_schedule_weekday_tuesday
import com.wynime.app.ui.lang.exploration_schedule_weekday_wednesday
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource

fun ScheduleScreenState(
    daysProvider: () -> List<ScheduleDay>,
): ScheduleScreenState {
    return ScheduleScreenState(
        initialSelected = daysProvider().firstOrNull { it.kind == ScheduleDay.Kind.TODAY },
        daysProvider = daysProvider,
    )
}

@Stable
class ScheduleScreenState(
    initialSelected: ScheduleDay?,
    daysProvider: () -> List<ScheduleDay>,
) {
    val days by derivedStateOf(daysProvider)

    internal val pagerState = PagerState(
        currentPage = days.indexOf(initialSelected).coerceAtLeast(0),
    ) { days.size }

    val lazyListState = LazyListState(firstVisibleItemIndex = pagerState.currentPage)

    val selectedDay: ScheduleDay? by derivedStateOf {
        days.getOrNull(pagerState.currentPage)
    }

    val scheduleColumnLazyListStates by derivedStateOf {
        days.associateWith {
            LazyListState()
        }
    }

    suspend fun scrollTo(day: ScheduleDay) {
        pagerState.scrollToPage(days.indexOf(day).coerceAtLeast(0))
    }

    suspend fun animateScrollTo(day: ScheduleDay) {
        pagerState.animateScrollToPage(days.indexOf(day).coerceAtLeast(0))
    }
}

@Composable
fun ScheduleScreen(
    presentation: SchedulePagePresentation,
    onRetry: () -> Unit,
    onClickItem: (item: AiringScheduleItemPresentation) -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: ScheduleScreenLayoutParams = ScheduleScreenLayoutParams.calculate(),
    colors: ScheduleScreenColors = ScheduleScreenDefaults.colors(),
    navigationIcon: @Composable () -> Unit = {},
    state: ScheduleScreenState = remember { ScheduleScreenState { presentation.days } },
    windowInsets: WindowInsets = WynimeWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier,
        topBar = {
            WynimeTopAppBar(
                title = { Text(stringResource(Lang.exploration_schedule)) },
                Modifier.fillMaxWidth(),
                navigationIcon = navigationIcon,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        containerColor = WynimeThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        if (presentation.error != null) {
            LoadErrorCard(
                presentation.error,
                onRetry = onRetry,
                modifier = Modifier.padding(paddingValues)
                    .padding(layoutParams.pageContentPadding)
                    .padding(all = 16.dp),
            )
        } else {
            ScheduleScreenContent(
                state = state,
                modifier = Modifier.padding(paddingValues),
                layoutParams = layoutParams,
                colors = colors,
            ) { day ->
                ScheduleDayColumn(
                    onClickItem = onClickItem,
                    dayOfWeek = {
                        if (layoutParams.showDayOfWeekHeadline) {
                            DayOfWeekHeadline(day)
                        }
                    },
                    items = presentation.airingSchedules.firstOrNull { it.date == day.date }?.episodes.orEmpty(),
                    layoutParams = layoutParams.columnLayoutParams,
                    state = state.scheduleColumnLazyListStates[day] ?: rememberLazyListState(),
                    itemColors = colors.itemColors,
                )
            }
        }
    }
}

@Composable
private fun DayOfWeekHeadline(
    day: ScheduleDay,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(IntrinsicSize.Min)) {
        Text(
            renderScheduleDay(day), Modifier.width(IntrinsicSize.Max),
            softWrap = false, textAlign = TextAlign.Start,
            color = if (day.kind == ScheduleDay.Kind.TODAY) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )

        val thickness = 2.dp
        val color = MaterialTheme.colorScheme.outlineVariant
        Canvas(
            Modifier.padding(top = 2.dp)
                .fillMaxWidth()
                .height(thickness),
        ) {
            drawLine(
                color = color,
                strokeWidth = thickness.toPx(),
                start = Offset(0f, thickness.toPx() / 2),
                end = Offset(size.width, thickness.toPx() / 2),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ScheduleScreenContent(
    state: ScheduleScreenState,
    modifier: Modifier = Modifier,
    layoutParams: ScheduleScreenLayoutParams = ScheduleScreenLayoutParams.calculate(),
    colors: ScheduleScreenColors = ScheduleScreenDefaults.colors(),
    pageContent: @Composable (page: ScheduleDay) -> Unit,
) {
    Column(modifier) {
        val uiScope = rememberCoroutineScope()
        if (layoutParams.showTabRow) {
            ScrollableTabRow(
                selectedTabIndex = state.pagerState.currentPage,
                containerColor = colors.tabRowContainerColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.pagerTabIndicatorOffset(state.pagerState, tabPositions),
                    )
                },
            ) {
                state.days.forEach { day ->
                    Tab(
                        selected = state.selectedDay == day,
                        onClick = {
                            uiScope.launch {
                                state.animateScrollTo(day)
                            }
                        },
                        text = {
                            Text(
                                renderScheduleDay(day), softWrap = false, textAlign = TextAlign.Center,
                                color = if (day.kind == ScheduleDay.Kind.TODAY) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        },
                        selectedContentColor = colors.tabSelectedContentColor,
                        unselectedContentColor = colors.tabUnselectedContentColor,
                    )
                }
            }
        }

        val density = LocalDensity.current

        if (LocalPlatform.current.isDesktop() && !layoutParams.isSinglePage) {

            HorizontalScrollControlScaffoldOnDesktop(
                rememberHorizontalScrollControlState(
                    state.lazyListState,
                    onClickScroll = { direction ->
                        uiScope.launch {
                            state.lazyListState.animateScrollBy(
                                with(density) { (300.dp).toPx() } *
                                        if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1,
                            )
                        }
                    },
                ),
            ) {
                LazyRow(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(layoutParams.pageSpacing),
                    state = state.lazyListState,
                ) {
                    items(state.days) { day ->
                        val widthModifier = when (val pageSize = layoutParams.pageSize) {
                            PageSize.Fill -> Modifier.fillMaxWidth()
                            is PageSize.Fixed -> Modifier.width(pageSize.pageSize)
                            else -> Modifier
                        }
                        Box(widthModifier.fillParentMaxHeight().padding(layoutParams.pageContentPadding)) {
                            pageContent(day)
                        }
                    }
                }
            }
        } else {
            HorizontalPager(
                state.pagerState,
                Modifier.fillMaxSize(),
                pageSize = layoutParams.pageSize,
                pageSpacing = layoutParams.pageSpacing,
                contentPadding = layoutParams.pageContentPadding,
                verticalAlignment = Alignment.Top,
                key = { it },
            ) { index ->
                Box(Modifier.fillMaxSize()) {
                    state.days.getOrNull(index)?.let {
                        pageContent(it)
                    }
                }
            }
        }
    }
}

@Immutable
@ExposedCopyVisibility
data class ScheduleScreenLayoutParams private constructor(
    val pageSize: PageSize,
    val pageSpacing: Dp,
    val pageContentPadding: PaddingValues,
    val showTabRow: Boolean,
    val showDayOfWeekHeadline: Boolean,
    val columnLayoutParams: ScheduleDayColumnLayoutParams,
    val isSinglePage: Boolean,
) {
    @Stable
    companion object {
        @Stable
        val Compact = ScheduleScreenLayoutParams(
            pageSize = PageSize.Fill,
            pageSpacing = 8.dp,
            pageContentPadding = PaddingValues(0.dp),
            showTabRow = true,
            showDayOfWeekHeadline = false,
            columnLayoutParams = ScheduleDayColumnLayoutParams.Default,
            isSinglePage = true,
        )

        @Stable
        val Medium = ScheduleScreenLayoutParams(
            pageSize = PageSize.Fixed(360.dp),
            pageSpacing = 16.dp,
            pageContentPadding = PaddingValues(horizontal = 8.dp),
            showTabRow = false,
            showDayOfWeekHeadline = true,
            columnLayoutParams = ScheduleDayColumnLayoutParams.Default,
            isSinglePage = false,
        )

        @Composable
        fun calculate(windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass): ScheduleScreenLayoutParams {
            return if (windowSizeClass.isWidthAtLeastMedium) {
                Medium
            } else {
                Compact
            }
        }
    }
}

@Immutable
data class ScheduleScreenColors(
    val tabRowContainerColor: Color,
    val tabSelectedContentColor: Color,
    val tabUnselectedContentColor: Color,
    val itemColors: ListItemColors,
)

@Stable
object ScheduleScreenDefaults {
    @Composable
    fun colors(
        tabRowColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tabSelectedContentColor: Color = contentColorFor(tabRowColor),
        tabUnselectedContentColor: Color = contentColorFor(tabRowColor),
        itemColors: ListItemColors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ): ScheduleScreenColors = ScheduleScreenColors(
        tabRowContainerColor = tabRowColor,
        tabSelectedContentColor = tabSelectedContentColor,
        tabUnselectedContentColor = tabUnselectedContentColor,
        itemColors = itemColors,
    )
}

@Stable
@Composable
private fun renderScheduleDay(day: ScheduleDay): String {
    val date = day.date
    return """
        ${date.month.number}/${date.day}
        ${renderDayOfWeek(day.dayOfWeek, day.kind)}
    """.trimIndent()
}

@Stable
@Suppress("REDUNDANT_ELSE_IN_WHEN")
@Composable
private fun renderDayOfWeek(day: DayOfWeek, kind: ScheduleDay.Kind): String {
    val weekday = when (day) {
        DayOfWeek.MONDAY -> stringResource(Lang.exploration_schedule_weekday_monday)
        DayOfWeek.TUESDAY -> stringResource(Lang.exploration_schedule_weekday_tuesday)
        DayOfWeek.WEDNESDAY -> stringResource(Lang.exploration_schedule_weekday_wednesday)
        DayOfWeek.THURSDAY -> stringResource(Lang.exploration_schedule_weekday_thursday)
        DayOfWeek.FRIDAY -> stringResource(Lang.exploration_schedule_weekday_friday)
        DayOfWeek.SATURDAY -> stringResource(Lang.exploration_schedule_weekday_saturday)
        DayOfWeek.SUNDAY -> stringResource(Lang.exploration_schedule_weekday_sunday)
        else -> day.toString()
    }

    return when (kind) {
        ScheduleDay.Kind.LAST_WEEK -> stringResource(Lang.exploration_schedule_last_weekday, weekday)
        ScheduleDay.Kind.THIS_WEEK,
        ScheduleDay.Kind.TODAY -> stringResource(Lang.exploration_schedule_this_weekday, weekday)

        ScheduleDay.Kind.NEXT_WEEK -> stringResource(Lang.exploration_schedule_next_weekday, weekday)
    }
}

@Composable
@Preview
fun PreviewSchedulePage() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            ScheduleScreen(
                presentation = createTestSchedulePagePresentation(),
                onRetry = {},
                onClickItem = {},
                navigationIcon = {
                    BackNavigationIconButton({})
                },
            )
        }
    }
}
