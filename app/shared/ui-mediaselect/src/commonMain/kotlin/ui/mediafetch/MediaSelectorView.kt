/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.TestMatchMetadata
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.icons.EditSquare
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_view_detailed_mode
import me.him188.ani.app.ui.lang.media_selector_view_simple_mode
import me.him188.ani.app.ui.lang.settings_media_source_more
import me.him188.ani.app.ui.mediafetch.request.MediaFetchRequestEditorDialog
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.mediaselect.selector.MediaSelectorWebSourcesColumn
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource


private inline val WINDOW_VERTICAL_PADDING get() = 8.dp

// For search: "数据源"
/**
 * 通用的数据源选择器. See preview
 */
@Composable
fun MediaSelectorView(
    state: MediaSelectorState,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    onRestartSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClickItem: (Media) -> Unit = { state.select(it) },
    scrollable: Boolean = true,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier) {
        // 编辑查询请求的对话框
        var showEditRequest by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        var isDetailedMode by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        if (showEditRequest && fetchRequest != null) {
            MediaFetchRequestEditorDialog(
                fetchRequest,
                onDismissRequest = { showEditRequest = false },
                onFetchRequestChange = {
                    onFetchRequestChange(it)
                    showEditRequest = false
                },
            )
        }

        MediaSelectorActionsRow(
            isDetailedMode = isDetailedMode,
            onDetailedModeChange = { isDetailedMode = it },
            onRequestFetchRequestEdit = { showEditRequest = true },
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        if (isDetailedMode) {
            MediaSelectorDetailedList(
                presentation = presentation,
                state = state,
                scope = scope,
                onClickItem = onClickItem,
                modifier = Modifier.padding(bottom = WINDOW_VERTICAL_PADDING)
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
            )
        } else {
            MediaSelectorWebSourcesColumn(
                presentation.webSources,
                selectedSource = { presentation.selectedWebSource },
                selectedChannel = { presentation.selectedWebSourceChannel },
                onSelect = { _, channel ->
                    channel.original?.let { onClickItem(it) }
                },
                onRefresh = { onRestartSource(it.instanceId) },
                onResolveCaptcha = { source ->
                    scope.launch {
                        if (state.resolveCaptcha(source)) {
                            onRestartSource(source.instanceId)
                        }
                    }
                },
                onRequestQueryEdit = { showEditRequest = true },
                Modifier.padding(bottom = WINDOW_VERTICAL_PADDING)
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
            )
        }
    }

}

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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            onClick = { onDetailedModeChange(false) },
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (!isDetailedMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        ) {
            Text(stringResource(Lang.media_selector_view_simple_mode))
        }
        TextButton(
            onClick = { onDetailedModeChange(true) },
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (isDetailedMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        ) {
            Text(stringResource(Lang.media_selector_view_detailed_mode))
        }
        Box(Modifier.weight(1f))
        Box {
            IconButton(onRequestFetchRequestEdit) {
                Icon(Icons.Rounded.EditSquare, contentDescription = stringResource(Lang.settings_media_source_more))
            }
//            DropdownMenu(showDropdown, { showDropdown = false }) {
//                DropdownMenuItem(
//                    text = { Text("编辑查询请求") },
//                    onClick = {
//                        showEditRequest = true
//                        showDropdown = false
//                    },
//                )
//            }

            // 编辑请求
        }
    }
}

@Composable
private fun MediaSelectorDetailedList(
    presentation: MediaSelectorState.Presentation,
    state: MediaSelectorState,
    scope: CoroutineScope,
    onClickItem: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = presentation.groupedMediaListIncluded + presentation.groupedMediaListExcluded
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
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


///////////////////////////////////////////////////////////////////////////
// Previews
///////////////////////////////////////////////////////////////////////////


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
