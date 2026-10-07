package com.wynime.app.ui.subject.episode.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.preference.EpisodeListProgressTheme

@Composable
fun EpisodeListFlowRow(
    episodes: List<EpisodeListItem>,
    onClick: (episode: EpisodeListItem) -> Unit,
    onLongClick: (episode: EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (episode in episodes) {
            EpisodeSortSquareButton(
                episode,
                onClick = { onClick(episode) },
                onLongClick = { onLongClick(episode) },
                colors = EpisodeListDefaults.colors(theme),
            )
        }
    }
}

