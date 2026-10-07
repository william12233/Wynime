package com.wynime.app.ui.subject.episode.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wynime.app.data.models.preference.EpisodeListProgressTheme
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.lists.ScrollStateVerticalScrollbar
import com.wynime.app.ui.foundation.lists.hasScrollableContent
import com.wynime.app.ui.foundation.theme.stronglyWeaken
import com.wynime.app.ui.foundation.theme.weaken
import com.wynime.app.ui.lang.*
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.*

@Composable
fun EpisodeListDialog(
    state: EpisodeListUiState,
    onDismissRequest: () -> Unit,
    onCacheClick: () -> Unit,
    onEpisodeClick: (episode: EpisodeListItem) -> Unit,
    onCollectionUpdate: (episode: EpisodeListItem) -> Unit,
    onSubjectDetailsClick: (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val scrollState = rememberScrollState()
    val scrollbarEndPadding = if (scrollState.hasScrollableContent()) 16.dp else 0.dp
    Dialog(onDismissRequest, properties) {
        Card {
            Box {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text(
                            stringResource(Lang.subject_episode_select_play),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.weight(1f))
                    }

                    Row(Modifier.padding(top = 8.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
                            Text(if (useOriginalTitle) state.subjectOriginalTitle else state.subjectTitle)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        Modifier.weight(1f, fill = false)
                            .heightIn(max = 360.dp),
                    ) {
                        Column(
                            Modifier.verticalScroll(scrollState).padding(end = scrollbarEndPadding),
                        ) {
                            EpisodeListFlowRow(
                                state.mainEpisodes,
                                onEpisodeClick,
                                onCollectionUpdate,
                            )

                            if (state.otherEpisodes.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                                EpisodeListFlowRow(
                                    state.otherEpisodes,
                                    onEpisodeClick,
                                    onCollectionUpdate,
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }

                        Box(Modifier.matchParentSize()) {
                            ScrollStateVerticalScrollbar(
                                state = scrollState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                            )
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null)

                        Text(
                            stringResource(Lang.subject_episode_long_press_mark_watched),
                            Modifier.padding(start = 4.dp),
                        )
                    }

                    Row(Modifier.padding(top = 16.dp).align(Alignment.End)) {
                        onSubjectDetailsClick?.let {
                            TextButton(
                                {
                                    onDismissRequest()
                                    it()
                                },
                            ) {
                                Text(stringResource(Lang.subject_episode_details))
                            }
                        }

                        TextButton(onDismissRequest, Modifier.padding(start = 8.dp)) {
                            Text(stringResource(Lang.subject_episode_close))
                        }
                    }
                }

                IconButton(onCacheClick, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Download, stringResource(Lang.subject_episode_cache))
                }
            }
        }
    }
}

@Immutable
class EpisodeListColors(

    val doneOrDroppedColor: Color,

    val canWatchColor: Color,

    val notPublishedColor: Color,
)

object EpisodeListDefaults {
    @Composable
    fun colors(
        theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
        action: Color = MaterialTheme.colorScheme.primary,
        disabled: Color = MaterialTheme.colorScheme.onSurface.stronglyWeaken(),
    ): EpisodeListColors {
        val dark = action.weaken()
        return when (theme) {
            EpisodeListProgressTheme.ACTION -> EpisodeListColors(
                doneOrDroppedColor = dark,
                canWatchColor = action,
                notPublishedColor = disabled,
            )

            EpisodeListProgressTheme.LIGHT_UP -> EpisodeListColors(
                doneOrDroppedColor = action,
                canWatchColor = dark,
                notPublishedColor = disabled,
            )
        }
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialog() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            TestEpisodeListUiState,
            {}, {}, {}, {},
        )
    }
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialogVeryLong() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            TestEpisodeListUiStateVeryLong,
            {}, {}, {}, {},
        )
    }
}

@Composable
private fun PreviewEpisodeListFlowRowImpl(
    episodes: List<EpisodeListItem>,
    theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
) {
    EpisodeListFlowRow(
        episodes = episodes,
        onClick = {},
        onLongClick = {},
        theme = theme,
    )
}
