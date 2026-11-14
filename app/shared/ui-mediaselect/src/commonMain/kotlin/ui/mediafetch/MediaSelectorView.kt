package com.wynime.app.ui.mediafetch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.TestMatchMetadata
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.icons.EditSquare
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.media_selector_view_detailed_mode
import com.wynime.app.ui.lang.media_selector_view_filtered_count
import com.wynime.app.ui.lang.media_selector_view_show_excluded
import com.wynime.app.ui.lang.media_selector_view_simple_mode
import com.wynime.app.ui.lang.media_selector_query_online
import com.wynime.app.ui.lang.settings_media_source_more
import com.wynime.app.ui.mediafetch.request.MediaFetchRequestEditorDialog
import com.wynime.app.ui.mediafetch.request.TestMediaFetchRequest
import com.wynime.app.ui.mediaselect.selector.MediaSelectorWebSourcesColumn
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Immutable
data class MediaSelectorLocalCacheOption(
    val id: String,
    val title: String,
    val description: String? = null,
)

private inline val WINDOW_VERTICAL_PADDING get() = 8.dp

@Composable
fun MediaSelectorView(
    state: MediaSelectorState?,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    onRestartSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClickItem: (Media) -> Unit = { state?.select(it) },
    localCacheOptions: List<MediaSelectorLocalCacheOption> = emptyList(),
    selectedLocalCacheId: String? = null,
    onlineQueryEnabled: Boolean = true,
    allowOnlineQuery: Boolean = true,
    onSelectLocalCache: (String) -> Unit = {},
    onQueryOnlineSources: (() -> Unit)? = null,
    scrollable: Boolean = true,
) {
    val selectorState = state
    val presentationFlow: Flow<MediaSelectorState.Presentation?> =
        selectorState?.presentationFlow ?: flowOf(null)
    val sourceResultsFlow: Flow<MediaSourceResultListPresentation?> =
        selectorState?.sourceResultsPresentationFlow ?: flowOf(null)
    val presentation by presentationFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val sourceResults by sourceResultsFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val showOnlinePicker = onlineQueryEnabled || onQueryOnlineSources == null
    val showLocalCacheChoices = localCacheOptions.isNotEmpty() ||
        (!onlineQueryEnabled && allowOnlineQuery && onQueryOnlineSources != null)
    val presentationState = presentation
    val sourceResultsState = sourceResults

    Column(modifier) {

        if (showLocalCacheChoices) {
            LocalCacheChoices(
                options = localCacheOptions,
                selectedId = selectedLocalCacheId,
                onSelect = onSelectLocalCache,
                showCacheLabel = localCacheOptions.isNotEmpty(),
                onQueryOnlineSources = onQueryOnlineSources.takeIf { !onlineQueryEnabled && allowOnlineQuery },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        var showEditRequest by rememberSaveable { mutableStateOf(false) }
        var isDetailedMode by rememberSaveable { mutableStateOf(false) }
        if (showOnlinePicker && showEditRequest && fetchRequest != null) {
            MediaFetchRequestEditorDialog(
                fetchRequest,
                onDismissRequest = { showEditRequest = false },
                onFetchRequestChange = {
                    onFetchRequestChange(it)
                    showEditRequest = false
                },
            )
        }

        if (showOnlinePicker) {
            MediaSelectorActionsRow(
                isDetailedMode = isDetailedMode,
                onDetailedModeChange = { isDetailedMode = it },
                onRequestFetchRequestEdit = { showEditRequest = true },
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }

        if (!showOnlinePicker) {
            if (!showLocalCacheChoices) {
                MediaSelectorLoadingPlaceholder(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = WINDOW_VERTICAL_PADDING),
                )
            }
        } else if (selectorState == null || presentationState == null || sourceResultsState == null) {
            MediaSelectorLoadingPlaceholder(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = WINDOW_VERTICAL_PADDING),
            )
        } else if (isDetailedMode) {
            MediaSelectorDetailedList(
                presentation = presentationState,
                sourceResults = sourceResultsState,
                state = selectorState,
                scope = scope,
                onClickItem = onClickItem,
                onRestartSource = onRestartSource,
                modifier = Modifier.padding(bottom = WINDOW_VERTICAL_PADDING)
                    .weight(1f, fill = scrollable)
                    .fillMaxWidth()
                    .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
            )
        } else {
            MediaSelectorWebSourcesColumn(
                presentationState.webSources,
                selectedSource = { presentationState.selectedWebSource },
                selectedChannel = { presentationState.selectedWebSourceChannel },
                onSelect = { _, channel ->
                    channel.original?.let { onClickItem(it) }
                },
                onRefresh = { onRestartSource(it.instanceId) },
                onResolveCaptcha = { source ->
                    scope.launch {
                        if (selectorState.resolveCaptcha(source)) {
                            onRestartSource(source.instanceId)
                        }
                    }
                },
                onRequestQueryEdit = { showEditRequest = true },
                Modifier.padding(bottom = WINDOW_VERTICAL_PADDING)
                    .weight(1f, fill = scrollable)
                    .fillMaxWidth()
                    .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
            )
        }
    }

}

@Composable
private fun LocalCacheChoices(
    options: List<MediaSelectorLocalCacheOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    showCacheLabel: Boolean,
    onQueryOnlineSources: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showCacheLabel) {
            Text(
                stringResource(Lang.media_selector_summary_cached_file),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        options.forEach { option ->
            Card(
                onClick = { onSelect(option.id) },
                modifier = Modifier.fillMaxWidth().testTag("media-selector-local-cache-${option.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (option.id == selectedId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option.id == selectedId, onClick = null)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            option.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        option.description?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        onQueryOnlineSources?.let { onQuery ->
            Button(onClick = onQuery, modifier = Modifier.fillMaxWidth().testTag("media-selector-query-online")) {
                Text(stringResource(Lang.media_selector_query_online))
            }
        }
    }
}

@Composable
fun MediaSelectorLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaSelectorActionsRow(
    isDetailedMode: Boolean,
    onDetailedModeChange: (Boolean) -> Unit,
    onRequestFetchRequestEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                onClick = { onDetailedModeChange(false) },
                selected = !isDetailedMode,
            ) {
                Text(stringResource(Lang.media_selector_view_simple_mode), softWrap = false)
            }
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                onClick = { onDetailedModeChange(true) },
                selected = isDetailedMode,
            ) {
                Text(stringResource(Lang.media_selector_view_detailed_mode), softWrap = false)
            }
        }
        Box {
            IconButton(onRequestFetchRequestEdit) {
                Icon(Icons.Rounded.EditSquare, contentDescription = stringResource(Lang.settings_media_source_more))
            }

        }
    }
}

@Composable
private fun MediaSelectorDetailedList(
    presentation: MediaSelectorState.Presentation,
    sourceResults: MediaSourceResultListPresentation,
    state: MediaSelectorState,
    scope: CoroutineScope,
    onClickItem: (Media) -> Unit,
    onRestartSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExcluded by rememberSaveable { mutableStateOf(false) }
    val filteredCountText = stringResource(
        Lang.media_selector_view_filtered_count,
        presentation.preferredCandidates.size,
        presentation.filteredCandidates.size,
    )
    val showExcludedText = stringResource(
        Lang.media_selector_view_show_excluded,
        presentation.groupedMediaListExcluded.size,
    )
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        MediaSourceResultsView(
            sourceResults = sourceResults,
            mediaSelector = state,
            onRefresh = {
                sourceResults.webSources.forEach { onRestartSource(it.instanceId) }
            },
            onRestartSource = onRestartSource,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            filteredCountText,
            style = MaterialTheme.typography.titleMedium,
        )
        MediaSelectorFilters(
            resolution = state.resolution,
            subtitleLanguageId = state.subtitleLanguageId,
            alliance = state.alliance,
            modifier = Modifier.fillMaxWidth(),
        )

        presentation.groupedMediaListIncluded.forEach { group ->
            MediaSelectorItem(
                group = group,
                groupState = state.getGroupState(group.groupId),
                mediaSourceInfoProvider = state.mediaSourceInfoProvider,
                selected = group.list.any { it.result?.mediaId == presentation.selected?.mediaId },
                onSelect = onClickItem,
                preferredResolution = { presentation.resolution.finalSelected },
                onPreferResolution = { resolution ->
                    scope.launch { state.resolution.prefer(resolution) }
                },
                preferredSubtitleLanguageId = { presentation.subtitleLanguageId.finalSelected },
                onPreferSubtitleLanguageId = { languageId ->
                    scope.launch { state.subtitleLanguageId.prefer(languageId) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (presentation.groupedMediaListExcluded.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    showExcludedText,
                    Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Switch(showExcluded, { showExcluded = !showExcluded })
            }
        }

        if (showExcluded) {
            presentation.groupedMediaListExcluded.forEach { group ->
                MediaSelectorItem(
                    group = group,
                    groupState = state.getGroupState(group.groupId),
                    mediaSourceInfoProvider = state.mediaSourceInfoProvider,
                    selected = group.list.any { it.result?.mediaId == presentation.selected?.mediaId },
                    onSelect = onClickItem,
                    preferredResolution = { presentation.resolution.finalSelected },
                    onPreferResolution = { resolution ->
                        scope.launch { state.resolution.prefer(resolution) }
                    },
                    preferredSubtitleLanguageId = { presentation.subtitleLanguageId.finalSelected },
                    onPreferSubtitleLanguageId = { languageId ->
                        scope.launch { state.subtitleLanguageId.prefer(languageId) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@TestOnly
internal val previewMediaList = TestMediaList.run {
    listOf(
        CachedMedia(
            origin = this[0],
            cacheMediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
            download = ResourceLocation.LocalFile("file://test.txt"),
        ),
    ) + this
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaSelector() {
    val scope = rememberCoroutineScope()
    val mediaSelector = rememberTestMediaSelectorPresentation(previewMediaList, scope)
    ProvideCompositionLocalsForPreview {
        Surface {
            MediaSelectorView(
                state = mediaSelector,
                fetchRequest = TestMediaFetchRequest,
                onFetchRequestChange = { },
                onRestartSource = {
                },
            )
        }
    }
}

@Composable
@OptIn(TestOnly::class)
private fun rememberTestMediaSelectorPresentation(previewMediaList: List<Media>, scope: CoroutineScope) =
    rememberMediaSelectorState(
        rememberTestMediaSourceInfoProvider(),
        createTestMediaSourceResultsFilterer(scope).filteredSourceResults,
    ) {
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = MutableStateFlow(
                listOf(
                    CachedMedia(
                        origin = previewMediaList[0],
                        cacheMediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
                        download = ResourceLocation.LocalFile("file://test.txt"),
                    ),
                ) + previewMediaList,
            ),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(
                MediaPreference.PlatformDefault.copy(
                    subtitleLanguageId = "CHS",
                ),
            ),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.AllVisible),
        )
    }

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaItemIncluded(modifier: Modifier = Modifier) = ProvideCompositionLocalsForPreview {
    MediaSelectorItem(
        remember {
            MediaGroupBuilder("Test").apply {
                add(previewMediaList[0].let { MaybeExcludedMedia.Included(it, TestMatchMetadata) })
            }.build()
        },
        remember { MediaGroupState("test") },
        rememberTestMediaSourceInfoProvider(),
        selected = false,
        onSelect = {},
        preferredResolution = { null },
        onPreferResolution = {},
        preferredSubtitleLanguageId = { null },
        onPreferSubtitleLanguageId = {},
        modifier = modifier,
    )
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaItemExcluded(modifier: Modifier = Modifier) = ProvideCompositionLocalsForPreview {
    MediaSelectorItem(
        remember {
            MediaGroupBuilder("Test").apply {
                add(previewMediaList[0].let { MaybeExcludedMedia.Excluded(it, MediaExclusionReason.FromSequelSeason) })
            }.build()
        },
        remember { MediaGroupState("test") },
        rememberTestMediaSourceInfoProvider(),
        selected = false,
        onSelect = {},
        preferredResolution = { null },
        onPreferResolution = {},
        preferredSubtitleLanguageId = { null },
        onPreferSubtitleLanguageId = {},
        modifier = modifier,
    )
}
