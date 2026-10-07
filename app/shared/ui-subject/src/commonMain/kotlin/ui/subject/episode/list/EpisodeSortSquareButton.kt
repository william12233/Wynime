package com.wynime.app.ui.subject.episode.list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.FilledTonalCombinedClickButton

@Composable
internal fun EpisodeSortSquareButton(
    item: EpisodeListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EpisodeListColors = EpisodeListDefaults.colors(),
) {
    val containerColor = when {
        item.isDoneOrDropped -> colors.doneOrDroppedColor
        !item.isBroadcast -> colors.notPublishedColor
        else -> colors.canWatchColor
    }
    FilledTonalCombinedClickButton(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(item.sort.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}
