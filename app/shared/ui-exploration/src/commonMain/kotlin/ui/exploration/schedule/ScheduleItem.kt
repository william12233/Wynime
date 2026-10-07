package com.wynime.app.ui.exploration.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.layout.paddingIfNotEmpty
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_schedule_episode
import com.wynime.app.ui.lang.exploration_schedule_episode_ep_and_sort
import com.wynime.app.ui.lang.exploration_schedule_time_unknown
import com.wynime.app.ui.lang.exploration_schedule_view_details
import com.wynime.datasources.api.EpisodeSort
import org.jetbrains.compose.resources.stringResource

@Composable
fun ScheduleItem(
    onClick: () -> Unit,
    subjectTitle: @Composable () -> Unit,
    episode: @Composable () -> Unit,
    leadingImage: @Composable () -> Unit,
    time: @Composable () -> Unit,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {

    Column {
        Row(Modifier.paddingIfNotEmpty(horizontal = 16.dp).paddingIfNotEmpty(top = 8.dp)) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                time()
            }
        }
        ListItem(
            headlineContent = subjectTitle,
            supportingContent = episode,
            leadingContent = {
                Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.small)) {
                    leadingImage()
                }
            },
            trailingContent = action,
            colors = colors,
            modifier = modifier.clickable(
                role = Role.Button,
                onClickLabel = stringResource(Lang.exploration_schedule_view_details),
                onClick = onClick,
            ),
        )
    }

}

@Composable
fun HorizontalDividerWithSubhead(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    dividerColor: Color = DividerDefaults.color,
    subheadAlignment: Alignment.Horizontal = Alignment.Start,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    subhead: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = verticalArrangement) {
        HorizontalDivider(thickness = thickness, color = dividerColor)

        ProvideTextStyle(textStyle) {
            Box(Modifier.align(subheadAlignment)) {
                subhead()
            }
        }
    }
}

object ScheduleItemDefaults {
    @Composable
    fun SubjectTitle(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        Text(text, overflow = TextOverflow.Ellipsis, maxLines = 1, modifier = modifier)
    }

    @Composable
    fun Episode(
        episodeSort: EpisodeSort,
        episodeEp: EpisodeSort?,
        episodeName: String?,
        modifier: Modifier = Modifier,
    ) {
        val epText = episodeEp?.toString()?.removePrefix("0")
        val sortText = episodeSort.toString().removePrefix("0")
        val sortDisplay = if (episodeEp == null || episodeEp == episodeSort) {
            if (episodeSort is EpisodeSort.Normal) {
                stringResource(Lang.exploration_schedule_episode, sortText)
            } else {
                sortText
            }
        } else {
            check(epText != null)
            if (episodeSort is EpisodeSort.Normal && episodeEp is EpisodeSort.Normal) {
                stringResource(Lang.exploration_schedule_episode_ep_and_sort, epText, sortText)
            } else {
                "$epText ($sortText)"
            }
        }
        val text = if (episodeName == null) sortDisplay else "$sortDisplay  $episodeName"
        Text(
            text,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = modifier,
        )
    }

    @Composable
    fun Time(
        time: LocalTime?,
        modifier: Modifier = Modifier
    ) {
        val text = renderTime(null, time, timeUnknownText = stringResource(Lang.exploration_schedule_time_unknown))
        Text(
            text,
            modifier,
            textAlign = TextAlign.End,
            softWrap = false,
            maxLines = 2,
            fontWeight = FontWeight.SemiBold,
        )
    }

    private val timeFormatter = LocalTime.Format {
        hour()
        char(':')
        minute()
    }

    fun renderTime(
        futureStartDate: LocalDate?,
        time: LocalTime,
    ): String = withFutureStartDate(futureStartDate, timeFormatter.format(time))

    fun renderTime(
        futureStartDate: LocalDate?,
        time: LocalTime?,
        timeUnknownText: String,
    ): String = withFutureStartDate(futureStartDate, if (time == null) timeUnknownText else timeFormatter.format(time))

    private fun withFutureStartDate(futureStartDate: LocalDate?, timeString: String): String {
        return if (futureStartDate != null) {
            "${futureStartDate.month.number}/${futureStartDate.day}\n${timeString}"
        } else {
            timeString
        }
    }
}
