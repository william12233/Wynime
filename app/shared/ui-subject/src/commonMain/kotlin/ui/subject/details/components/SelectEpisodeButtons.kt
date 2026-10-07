package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.TestSubjectProgressInfos
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.subject.SubjectProgressState
import com.wynime.app.ui.subject.collection.progress.SubjectProgressButton
import com.wynime.app.ui.subject.collection.progress.rememberTestSubjectProgressState
import com.wynime.utils.platform.annotations.TestOnly

@Suppress("UnusedReceiverParameter")
@Composable
fun SubjectDetailsDefaults.SelectEpisodeButtons(
    state: SubjectProgressState,
    onShowEpisodeList: () -> Unit,
    onPlay: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onShowEpisodeList) {
            Icon(Icons.Outlined.Dataset, null)
        }

        Box(Modifier.weight(1f)) {
            SubjectProgressButton(
                state,
                onPlay = {
                    state.episodeIdToPlay?.let(onPlay)
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSelectEpisodeButtonsDone() {
    PreviewSelectEpisodeButtonsImpl(TestSubjectProgressInfos.Done)
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSelectEpisodeButtonsContinueWatching() {
    PreviewSelectEpisodeButtonsImpl(TestSubjectProgressInfos.ContinueWatching2)
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSelectEpisodeButtonsWatched() {
    PreviewSelectEpisodeButtonsImpl(TestSubjectProgressInfos.Watched2)
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSelectEpisodeButtonsNotOnAir() {
    PreviewSelectEpisodeButtonsImpl(TestSubjectProgressInfos.NotOnAir)
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewSelectEpisodeButtonsImpl(progressInfo: SubjectProgressInfo) {
    ProvideCompositionLocalsForPreview {
        Surface {
            SubjectDetailsDefaults.SelectEpisodeButtons(
                state = rememberTestSubjectProgressState(
                    progressInfo,
                ),
                onShowEpisodeList = {},
                onPlay = {},
            )
        }
    }
}
