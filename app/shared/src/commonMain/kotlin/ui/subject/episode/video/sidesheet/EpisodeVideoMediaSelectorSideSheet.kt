package com.wynime.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_close_selector
import com.wynime.app.ui.lang.subject_episode_select_media_source
import com.wynime.app.ui.mediafetch.MediaSelectorState
import com.wynime.app.ui.mediafetch.MediaSelectorView
import com.wynime.app.ui.mediafetch.rememberTestMediaSelectorState
import com.wynime.app.ui.mediafetch.request.TestMediaFetchRequest
import com.wynime.app.ui.subject.episode.TAG_MEDIA_SELECTOR_SHEET
import com.wynime.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import com.wynime.app.ui.subject.episode.video.settings.SideSheetLayout
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.MediaSelectorSheet(
    mediaSelectorState: MediaSelectorState,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    onDismissRequest: () -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    val closeSelectorText = stringResource(Lang.subject_episode_close_selector)

    SideSheetLayout(
        title = { Text(text = selectMediaSourceText) },
        onDismissRequest = onDismissRequest,
        Modifier.testTag(TAG_MEDIA_SELECTOR_SHEET),
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = closeSelectorText)
            }
        },
    ) {
        MediaSelectorView(
            state = mediaSelectorState,
            fetchRequest = fetchRequest,
            onFetchRequestChange = onFetchRequestChange,
            onRestartSource = onRestartSource,
            modifier = modifier.padding(horizontal = 16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            onClickItem = {
                mediaSelectorState.select(it)
                onDismissRequest()
            },
        )
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
@PreviewLightDark
private fun PreviewEpisodeVideoMediaSelectorSideSheet() {
    ProvideCompositionLocalsForPreview {
        EpisodeVideoSideSheets.MediaSelectorSheet(
            mediaSelectorState = rememberTestMediaSelectorState(),
            fetchRequest = TestMediaFetchRequest,
            onFetchRequestChange = {},
            onDismissRequest = {},
            onRestartSource = {},
        )
    }
}
