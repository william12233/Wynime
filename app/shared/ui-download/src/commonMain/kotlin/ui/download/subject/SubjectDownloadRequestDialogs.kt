package com.wynime.app.ui.download.subject

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.download.DownloadEpisodeOption
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceResultsFilterer
import com.wynime.app.domain.media.fetch.restart
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.layout.desktopTitleBarPadding
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_management_episode_label
import com.wynime.app.ui.lang.cache_subject_cache
import com.wynime.app.ui.lang.cache_subject_cancel
import com.wynime.app.ui.lang.downloads_create_failed
import com.wynime.app.ui.lang.downloads_episode_picker_all
import com.wynime.app.ui.lang.downloads_episode_picker_back
import com.wynime.app.ui.lang.downloads_episode_picker_current
import com.wynime.app.ui.lang.downloads_episode_picker_downloaded
import com.wynime.app.ui.lang.downloads_episode_picker_from_current
import com.wynime.app.ui.lang.downloads_episode_picker_line
import com.wynime.app.ui.lang.downloads_episode_picker_only_current
import com.wynime.app.ui.lang.downloads_episode_picker_selected_count
import com.wynime.app.ui.lang.downloads_episode_picker_title
import com.wynime.app.ui.lang.downloads_episode_picker_unmatched
import com.wynime.app.ui.mediafetch.MediaSelectorView
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider
import com.wynime.app.ui.mediafetch.rememberMediaSelectorState
import com.wynime.datasources.api.Media
import org.jetbrains.compose.resources.stringResource

data class DownloadRequestDialogState(

    val selection: DownloadMediaPickerState? = null,

    val episodePicker: DownloadEpisodePickerState? = null,
    val failed: Boolean = false,
)

class DownloadMediaPickerState(
    val episodeId: Int,
    val fetchSession: MediaFetchSession,
    val selector: MediaSelector,
)

class DownloadEpisodePickerState(
    val episodeId: Int,
    val chosen: Media,
    val options: List<DownloadEpisodeOption>,
)

internal object DownloadEpisodePickerTestTags {
    const val ROOT = "download_episode_picker"
    const val BACK = "download_episode_picker_back"
    const val ONLY_CURRENT = "download_episode_picker_only_current"
    const val FROM_CURRENT = "download_episode_picker_from_current"
    const val ALL = "download_episode_picker_all"
    const val SELECTED_COUNT = "download_episode_picker_selected_count"
    const val CONFIRM = "download_episode_picker_confirm"
    fun row(episodeId: Int) = "download_episode_picker_row_$episodeId"
}

@Composable
internal fun SubjectDownloadRequestDialogs(
    state: DownloadRequestDialogState,
    visible: Boolean,
    sourceInfoProvider: MediaSourceInfoProvider,
    settings: Flow<MediaSelectorSettings>,
    onHide: () -> Unit,
    onSelectMedia: (Int, Media) -> Unit,
    onConfirmEpisodes: (Set<Int>) -> Unit,
    onBackToMediaSelection: () -> Unit,
    onCancel: () -> Unit,
) {
    val selection = state.selection
    if (visible && selection != null) {
        key(selection) {
            DownloadRequestSheet(
                selection,
                state.episodePicker,
                sourceInfoProvider,
                settings,
                onDismiss = onHide,
                onSelect = { onSelectMedia(selection.episodeId, it) },
                onConfirmEpisodes = onConfirmEpisodes,
                onBack = onBackToMediaSelection,
            )
        }
    }
    if (state.failed) {
        AlertDialog(
            onDismissRequest = onCancel,
            text = { Text(stringResource(Lang.downloads_create_failed)) },
            confirmButton = {
                TextButton(onClick = onCancel) { Text(stringResource(Lang.cache_subject_cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadRequestSheet(
    selection: DownloadMediaPickerState,
    episodePicker: DownloadEpisodePickerState?,
    sourceInfoProvider: MediaSourceInfoProvider,
    settings: Flow<MediaSelectorSettings>,
    onDismiss: () -> Unit,
    onSelect: (Media) -> Unit,
    onConfirmEpisodes: (Set<Int>) -> Unit,
    onBack: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
        contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
    ) {
        AnimatedContent(
            targetState = episodePicker,
            contentKey = { it != null },
            contentAlignment = Alignment.TopCenter,
        ) { picker ->
            if (picker == null) {
                DownloadMediaPicker(selection, sourceInfoProvider, settings, onSelect)
            } else {
                DownloadEpisodePicker(
                    picker,
                    onBack = onBack,
                    onConfirm = onConfirmEpisodes,
                    modifier = Modifier.padding(vertical = 12.dp)
                        .navigationBarsPadding().fillMaxHeight().fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DownloadMediaPicker(
    selection: DownloadMediaPickerState,
    sourceInfoProvider: MediaSourceInfoProvider,
    settings: Flow<MediaSelectorSettings>,
    onSelect: (Media) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val filteredResults = remember(selection, settings) {
        MediaSourceResultsFilterer(flowOf(selection.fetchSession.mediaSourceResults), settings, scope).filteredSourceResults
    }
    val selectorState = rememberMediaSelectorState(sourceInfoProvider, filteredResults) { selection.selector }
    val fetchRequest by selection.fetchSession.request.collectAsStateWithLifecycle(null)

    MediaSelectorView(
        state = selectorState,
        fetchRequest = fetchRequest,
        onFetchRequestChange = selection.fetchSession::setFetchRequest,
        onRestartSource = { selection.fetchSession.restart(it) },
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
            .navigationBarsPadding().fillMaxHeight().fillMaxWidth(),
        onClickItem = onSelect,
    )
}

@Composable
internal fun DownloadEpisodePicker(
    state: DownloadEpisodePickerState,
    onBack: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = state.options
    val selectable = remember(options) {
        options.filter { it.availability == DownloadEpisodeOption.Availability.AVAILABLE }
    }
    val onlyCurrent = remember(options) { selectable.filter { it.isCurrent }.mapTo(hashSetOf()) { it.episodeId } }
    val fromCurrent = remember(options) {
        val currentIndex = options.indexOfFirst { it.isCurrent }
        selectable.filter { options.indexOf(it) >= currentIndex }.mapTo(hashSetOf()) { it.episodeId }
    }
    val all = remember(options) { selectable.mapTo(hashSetOf()) { it.episodeId } }
    var selected by remember(state) { mutableStateOf<Set<Int>>(fromCurrent) }

    Column(modifier.testTag(DownloadEpisodePickerTestTags.ROOT)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, Modifier.testTag(DownloadEpisodePickerTestTags.BACK)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Lang.downloads_episode_picker_back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(Lang.downloads_episode_picker_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        Lang.downloads_episode_picker_line,
                        state.chosen.properties.alliance.ifBlank { state.chosen.originalTitle },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == onlyCurrent,
                onClick = { selected = onlyCurrent },
                label = { Text(stringResource(Lang.downloads_episode_picker_only_current)) },
                modifier = Modifier.testTag(DownloadEpisodePickerTestTags.ONLY_CURRENT),
            )
            FilterChip(
                selected = selected == fromCurrent,
                onClick = { selected = fromCurrent },
                label = { Text(stringResource(Lang.downloads_episode_picker_from_current)) },
                modifier = Modifier.testTag(DownloadEpisodePickerTestTags.FROM_CURRENT),
            )
            FilterChip(
                selected = selected == all,
                onClick = { selected = all },
                label = { Text(stringResource(Lang.downloads_episode_picker_all)) },
                modifier = Modifier.testTag(DownloadEpisodePickerTestTags.ALL),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(options, key = { it.episodeId }) { option ->
                EpisodeOptionRow(
                    option,
                    checked = option.episodeId in selected,
                    onToggle = {
                        selected = if (option.episodeId in selected) selected - option.episodeId else selected + option.episodeId
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Lang.downloads_episode_picker_selected_count, selected.size),
                Modifier.weight(1f).testTag(DownloadEpisodePickerTestTags.SELECTED_COUNT),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.testTag(DownloadEpisodePickerTestTags.CONFIRM),
            ) {
                Text(stringResource(Lang.cache_subject_cache))
            }
        }
    }
}

@Composable
private fun EpisodeOptionRow(
    option: DownloadEpisodeOption,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val enabled = option.availability == DownloadEpisodeOption.Availability.AVAILABLE
    val secondary = when (option.availability) {
        DownloadEpisodeOption.Availability.AVAILABLE -> option.resourceTitle.orEmpty()
        DownloadEpisodeOption.Availability.ALREADY_DOWNLOADED -> stringResource(Lang.downloads_episode_picker_downloaded)
        DownloadEpisodeOption.Availability.UNMATCHED -> stringResource(Lang.downloads_episode_picker_unmatched)
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(DownloadEpisodePickerTestTags.row(option.episodeId)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked && enabled, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Lang.cache_management_episode_label, option.sort, option.name),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary.isNotEmpty()) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.isCurrent) {
            Box(Modifier.padding(start = 4.dp)) {
                Text(
                    stringResource(Lang.downloads_episode_picker_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
