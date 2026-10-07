package com.wynime.app.ui.subject.episode.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.theme.LocalDarkOnSurface
import com.wynime.app.ui.foundation.theme.appColorScheme
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_unwatch
import com.wynime.app.ui.lang.subject_episode_watched_badge
import org.jetbrains.compose.resources.stringResource

@Stable
private val episodeStillBrush = Brush.verticalGradient(
    listOf(
        Color.Transparent,
        Color.Transparent,
        Color.Black.copy(alpha = 0.612f),
    ),
)

@Composable
fun EpisodeStillBackground(
    imageUrl: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag(EPISODE_STILL_TAG)) {

        key(imageUrl) {
            AsyncImage(
                imageUrl,
                contentDescription = null,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (highlighted) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
        }
        Box(Modifier.matchParentSize().background(episodeStillBrush))
    }
}

@Composable
fun EpisodeCellLabel(
    sort: String,
    name: String,
    sortColor: Color,
    nameColor: Color,
    modifier: Modifier = Modifier,
    playingIndicator: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        playingIndicator?.invoke()
        Text(
            sort,
            color = sortColor,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        Text(
            name,
            color = nameColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EpisodeWatchedBadge(
    onStill: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (onStill) Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (onStill) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        stringResource(Lang.subject_episode_watched_badge),
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable(role = Role.Button, onClickLabel = stringResource(Lang.subject_episode_unwatch), onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(EPISODE_WATCHED_BADGE_TAG),
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
    )
}

@Composable
fun EpisodePlayProgressBar(
    progress: Float,
    onStill: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = if (onStill) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(trackColor)
            .testTag(EPISODE_PROGRESS_TAG),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

object EpisodeStillDefaults {

    val contentColor: Color
        @Composable
        get() {
            val provided = LocalDarkOnSurface.current
            return if (provided.isSpecified) provided else appColorScheme(isDark = true).onSurface
        }

    val secondaryContentColor: Color
        @Composable
        get() = contentColor.copy(alpha = 0.85f)
}

const val EPISODE_STILL_TAG: String = "episode_still"

const val EPISODE_WATCHED_BADGE_TAG: String = "episode_watched_badge"

const val EPISODE_PROGRESS_TAG: String = "episode_play_progress"
