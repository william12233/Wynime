package com.wynime.app.ui.exploration.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.packInts
import kotlinx.datetime.LocalTime
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.layout.paddingIfNotEmpty
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.foundation.text.ProvideContentColor
import com.wynime.datasources.api.EpisodeSort

@Composable
fun ScheduleDayColumn(
    items: List<AiringScheduleColumnItem>,
    onClickItem: (item: AiringScheduleItemPresentation) -> Unit,
    dayOfWeek: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: ScheduleDayColumnLayoutParams = ScheduleDayColumnLayoutParams.Default,
    state: LazyListState = rememberLazyListState(),
    itemColors: ListItemColors = ListItemDefaults.colors(),
) {
    Column(modifier) {
        Row(Modifier.paddingIfNotEmpty(layoutParams.dayOfWeekPaddings)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                dayOfWeek()
            }
        }

        LazyColumn(
            Modifier.padding(layoutParams.listPadding),
            state = state,
            contentPadding = PaddingValues(vertical = 8.dp).plus(WindowInsets.navigationBars.asPaddingValues()),
            verticalArrangement = Arrangement.spacedBy(layoutParams.listVerticalSpacing),
        ) {
            items(
                items,
                key = { item ->
                    "ScheduleDayColumn-" + when (item) {
                        is AiringScheduleColumnItem.Data -> packInts(item.item.subjectId, item.item.episodeId)
                        is AiringScheduleColumnItem.CurrentTimeIndicator -> item.hashCode()
                        is AiringScheduleColumnItem.PlaceholderData -> item.id
                    }
                },
                contentType = { item ->
                    when (item) {
                        is AiringScheduleColumnItem.Data -> true
                        is AiringScheduleColumnItem.CurrentTimeIndicator -> false
                        is AiringScheduleColumnItem.PlaceholderData -> true
                    }
                },
            ) { columnItem ->
                when (columnItem) {
                    is AiringScheduleColumnItem.CurrentTimeIndicator -> {
                        ScheduleCurrentTimeIndicator(
                            columnItem,
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                .placeholder(columnItem.isPlaceholder, shape = RectangleShape),
                        )
                    }

                    is AiringScheduleColumnItem.Data -> {
                        val item = columnItem.item
                        val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
                        val subjectTitle = if (useOriginalTitle) item.subjectOriginalTitle else item.subjectTitle
                        val episodeName = if (useOriginalTitle) item.episodeOriginalName else item.episodeName
                        ScheduleItem(
                            onClick = { onClickItem(item) },
                            subjectTitle = {
                                ScheduleItemDefaults.SubjectTitle(
                                    subjectTitle,
                                )
                            },
                            episode = {
                                ScheduleItemDefaults.Episode(
                                    item.episodeSort,
                                    item.episodeEp,
                                    episodeName,
                                )
                            },
                            leadingImage = {
                                AsyncImage(
                                    item.imageUrl,
                                    "$subjectTitle 封面",
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            },
                            time = {
                                if (columnItem.showTime) {
                                    ScheduleItemDefaults.Time(item.time)
                                }
                            },
                            action = {

                            },
                            colors = itemColors,
                        )
                    }

                    is AiringScheduleColumnItem.PlaceholderData -> {
                        ScheduleItem(
                            onClick = {},
                            subjectTitle = {
                                ScheduleItemDefaults.SubjectTitle(
                                    "Placeholder",
                                    Modifier.placeholder(true, shape = RectangleShape),
                                )
                            },
                            episode = {
                                ScheduleItemDefaults.Episode(
                                    EpisodeSort(1),
                                    null,
                                    "Placeholder",
                                    Modifier.placeholder(true, shape = RectangleShape),
                                )
                            },
                            leadingImage = {
                                Box(Modifier.fillMaxSize().placeholder(true, shape = RectangleShape))
                            },
                            time = {
                                if (columnItem.showTime) {
                                    ScheduleItemDefaults.Time(
                                        dummyLocalTime,
                                        Modifier.placeholder(true, shape = RectangleShape),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

    }
}

private val dummyLocalTime = LocalTime(1, 2, 3)

@Composable
private fun ScheduleCurrentTimeIndicator(
    columnItem: AiringScheduleColumnItem.CurrentTimeIndicator,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideContentColor(MaterialTheme.colorScheme.primary) {
            Row(
                Modifier.padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Alarm, null)
                Text(
                    ScheduleItemDefaults.renderTime(null, columnItem.currentTime),
                    softWrap = false,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider()
        }
    }
}

@Immutable
sealed class AiringScheduleColumnItem {
    @Immutable
    data class Data(
        val item: AiringScheduleItemPresentation,
        val showTime: Boolean,
    ) : AiringScheduleColumnItem()

    @Immutable
    data class PlaceholderData(
        val id: Int,
        val showTime: Boolean,
    ) : AiringScheduleColumnItem()

    @Immutable
    data class CurrentTimeIndicator(
        val currentTime: LocalTime,
        val isPlaceholder: Boolean,
    ) : AiringScheduleColumnItem()
}

@Immutable
data class ScheduleDayColumnLayoutParams(
    val dayOfWeekPaddings: PaddingValues,
    val listVerticalSpacing: Dp,
    val listPadding: PaddingValues,
) {
    @Stable
    companion object {

        @Stable
        val Default = ScheduleDayColumnLayoutParams(
            dayOfWeekPaddings = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            listVerticalSpacing = 0.dp,
            listPadding = PaddingValues(0.dp),
        )
    }
}
