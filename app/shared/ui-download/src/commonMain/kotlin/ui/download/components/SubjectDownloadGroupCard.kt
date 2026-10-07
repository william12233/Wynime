package com.wynime.app.ui.download.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_management_finished_count
import com.wynime.datasources.api.topic.FileSize
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubjectDownloadGroupCard(
    group: SubjectDownloadGroup,
    selected: Boolean,
    selectionMode: Boolean,
    allEntriesSelected: Boolean,
    onToggleGroupSelection: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,

    showChevron: Boolean = true,

    shape: Shape = MaterialTheme.shapes.large,
) {
    val containerColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            selectionMode && allEntriesSelected -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
    )
    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = containerColor,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = allEntriesSelected,
                    onCheckedChange = { onToggleGroupSelection() },
                )
            }

            AsyncImage(
                group.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(44.dp)
                    .height(59.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    group.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val finishedCountText = stringResource(
                        Lang.cache_management_finished_count,
                        group.finishedCount,
                        group.displayTotalCount,
                    )
                    val metaText = if (group.totalSize != FileSize.Unspecified) {
                        "$finishedCountText · ${group.totalSize}"
                    } else {
                        finishedCountText
                    }
                    Text(
                        metaText,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    group.downloadSpeedText?.let { speedText ->
                        Text(
                            speedText,
                            Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                if (group.hasUnfinished) {
                    LinearProgressIndicator(
                        progress = { group.averageProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            if (!selectionMode && showChevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
