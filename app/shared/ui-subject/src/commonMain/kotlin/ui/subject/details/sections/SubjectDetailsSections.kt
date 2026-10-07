package com.wynime.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.ui.foundation.OutlinedTag
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_air_date
import com.wynime.app.ui.lang.subject_details_air_date_format
import com.wynime.app.ui.lang.subject_details_aliases
import com.wynime.app.ui.lang.subject_details_info
import com.wynime.app.ui.lang.subject_details_manage_cache
import com.wynime.app.ui.lang.subject_relation_graph_entry
import com.wynime.app.ui.lang.subject_details_show_less
import com.wynime.app.ui.lang.subject_details_show_more
import com.wynime.app.ui.lang.subject_details_total_episodes
import com.wynime.datasources.api.PackedDate
import org.jetbrains.compose.resources.stringResource

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        action()
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionHeader(title, modifier) {
        if (actionLabel != null) {
            SectionHeaderActionButton(onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionHeaderActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TextButton(onClick) {
        content()
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            Modifier.size(18.dp),
        )
    }
}

@Composable
fun SectionHeaderRelationGraphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick, modifier) {
        Icon(
            Icons.Rounded.AccountTree,
            contentDescription = null,
            Modifier.size(18.dp),
        )
        Text(stringResource(Lang.subject_relation_graph_entry), Modifier.padding(start = 4.dp))
    }
}

@Composable
fun SectionHeaderCacheButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    if (showLabel) {
        TextButton(onClick, modifier) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = null,
                Modifier.size(18.dp),
            )
            Text(stringResource(Lang.subject_details_manage_cache), Modifier.padding(start = 4.dp))
        }
    } else {
        IconButton(onClick, modifier) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = stringResource(Lang.subject_details_manage_cache),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SubjectSummarySection(
    summary: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 5,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                summary,
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow },
            )
        }
        if (hasOverflow || expanded) {
            TextButton(
                { expanded = !expanded },
                Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (expanded) Lang.subject_details_show_less else Lang.subject_details_show_more))
            }
        }
    }
}

private const val ALWAYS_SHOW_TAGS_COUNT = 8

@Composable
fun SubjectTagsSection(
    tags: List<Tag>,
    onClickTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val presentTags = remember(tags, expanded) {
        when {
            expanded -> tags
            tags.size <= 6 -> tags
            else -> {
                val hot = tags.filter { it.count > 100 }
                if (hot.size < ALWAYS_SHOW_TAGS_COUNT) tags.take(ALWAYS_SHOW_TAGS_COUNT) else hot
            }
        }
    }
    val hasMore = tags.size > ALWAYS_SHOW_TAGS_COUNT
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presentTags.forEach { tag ->
                OutlinedTag(Modifier.clickable { onClickTag(tag) }) {
                    Text(tag.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (hasMore) {
            TextButton(
                { expanded = !expanded },
                Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (expanded) Lang.subject_details_show_less else Lang.subject_details_show_more))
            }
        }
    }
}

@Composable
fun SubjectInfoTable(
    info: SubjectInfo,
    mainEpisodeCount: Int?,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    val rows = buildList {
        if (info.airDate.isValid) {
            add(stringResource(Lang.subject_details_air_date) to formatAirDate(info.airDate))
        }
        if (mainEpisodeCount != null && mainEpisodeCount > 0) {
            add(stringResource(Lang.subject_details_total_episodes) to mainEpisodeCount.toString())
        }
        if (info.aliases.isNotEmpty()) add(stringResource(Lang.subject_details_aliases) to info.aliases.joinToString(" / "))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
private fun formatAirDate(date: PackedDate): String = stringResource(
    Lang.subject_details_air_date_format,
    date.year.toString(),
    date.month.toString(),
    date.day.toString(),
)
