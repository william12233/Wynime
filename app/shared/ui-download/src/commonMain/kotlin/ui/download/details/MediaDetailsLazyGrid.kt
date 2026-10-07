package com.wynime.app.ui.download.details

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FilePresent
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.http.Url
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.features.getComponentAccessors
import com.wynime.app.tools.formatDateTime
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_details_browse_file
import com.wynime.app.ui.lang.cache_details_copied
import com.wynime.app.ui.lang.cache_details_copy
import com.wynime.app.ui.lang.cache_details_episode_range
import com.wynime.app.ui.lang.cache_details_external_subtitle
import com.wynime.app.ui.lang.cache_details_file_size
import com.wynime.app.ui.lang.cache_details_file_type
import com.wynime.app.ui.lang.cache_details_local_cache_path
import com.wynime.app.ui.lang.cache_details_open_file_failed
import com.wynime.app.ui.lang.cache_details_open_link
import com.wynime.app.ui.lang.cache_details_original_download_link
import com.wynime.app.ui.lang.cache_details_original_link
import com.wynime.app.ui.lang.cache_details_publish_time
import com.wynime.app.ui.lang.cache_details_resolution
import com.wynime.app.ui.lang.cache_details_source
import com.wynime.app.ui.lang.cache_details_source_local
import com.wynime.app.ui.lang.cache_details_source_online
import com.wynime.app.ui.lang.cache_details_subtitle_group
import com.wynime.app.ui.lang.cache_details_subtitle_language
import com.wynime.app.ui.lang.cache_unknown
import com.wynime.app.ui.media.MediaDetailsRenderer
import com.wynime.app.ui.media.rememberMediaDetailsStrings
import com.wynime.app.ui.settings.rendering.MediaSourceIcon
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaExtraFiles
import com.wynime.datasources.api.MediaProperties
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.isSingleEpisode
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.inSystem
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Immutable
data class MediaDetails(
    val originalTitle: String,
    val episodeRange: EpisodeRange?,
    val kind: MediaSourceKind,
    val originalUrl: String,
    val properties: MediaProperties,
    val fileSize: FileSize,
    val publishedTimeMillis: Long,

    val contentOriginalUri: String,
    val fileType: ResourceLocation.LocalFile.FileType?,

    val contentDownloadUri: String?,

    val localCacheFilePath: Path?,
    val extraFiles: MediaExtraFiles,
    val sourceInfo: MediaSourceInfo?,
) {
    val isUrlLegal = originalUrl.startsWith("http://", ignoreCase = true)
            || originalUrl.startsWith("https://", ignoreCase = true)

    companion object {
        fun from(
            originalMedia: Media,
            sourceInfo: MediaSourceInfo?,
            cachedMedia: CachedMedia?,
        ): MediaDetails {
            val originalUri = when (val download = originalMedia.download) {
                is ResourceLocation.HttpStreamingFile -> download.uri
                is ResourceLocation.LocalFile -> download.filePath
                is ResourceLocation.SourcePluginMedia -> download.uri
                is ResourceLocation.WebVideo -> download.uri
            }
            val fileType = when (val download = cachedMedia?.download ?: originalMedia.download) {
                is ResourceLocation.HttpStreamingFile -> null
                is ResourceLocation.LocalFile -> download.fileType
                is ResourceLocation.SourcePluginMedia -> null
                is ResourceLocation.WebVideo -> null
            }
            val contentDownloadUri = when (val download = cachedMedia?.download) {
                is ResourceLocation.LocalFile -> download.originalUri
                is ResourceLocation.HttpStreamingFile,
                is ResourceLocation.SourcePluginMedia,
                is ResourceLocation.WebVideo,
                null -> null
            }
            val localCacheFilePath = when (val download = cachedMedia?.download) {
                is ResourceLocation.LocalFile -> Path(download.filePath)
                is ResourceLocation.HttpStreamingFile,
                is ResourceLocation.SourcePluginMedia,
                is ResourceLocation.WebVideo,
                null -> null
            }

            return MediaDetails(
                originalTitle = originalMedia.originalTitle,
                episodeRange = originalMedia.episodeRange,
                kind = originalMedia.kind,
                originalUrl = originalMedia.originalUrl,
                properties = originalMedia.properties,
                fileSize = cachedMedia?.properties?.size ?: originalMedia.properties.size,
                publishedTimeMillis = originalMedia.publishedTime,
                contentOriginalUri = originalUri,
                fileType = fileType,
                contentDownloadUri = contentDownloadUri,
                localCacheFilePath = localCacheFilePath,
                extraFiles = originalMedia.extraFiles,
                sourceInfo = sourceInfo,
            )
        }
    }
}

@Composable
fun MediaDetailsLazyGrid(
    details: MediaDetails,
    modifier: Modifier = Modifier,
    showSourceInfo: Boolean = true,
    downloader: DownloaderDetails? = null,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val fileRevealer = LocalContext.current.getComponentAccessors().fileRevealer
    val scope = rememberCoroutineScope()
    val mediaDetailsStrings = rememberMediaDetailsStrings()

    val toaster = LocalToaster.current
    val copiedText = stringResource(Lang.cache_details_copied)
    val copyText = stringResource(Lang.cache_details_copy)
    val openLinkText = stringResource(Lang.cache_details_open_link)
    val browseFileText = stringResource(Lang.cache_details_browse_file)
    val unknownText = stringResource(Lang.cache_unknown)
    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 500.dp),
        modifier,
    ) {
        val copyContent = @Composable { value: () -> String ->
            IconButton(
                {
                    scope.launch {
                        clipboard.setClipEntryText(value())
                        toaster.toast(copiedText)
                    }
                },
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = copyText)
            }
        }
        val browseContent = @Composable { url: String ->
            IconButton(
                {
                    if (runCatching { Url(url) }.isSuccess) {
                        uriHandler.openUri(url)
                    } else {
                        scope.launch {
                            clipboard.setClipEntryText(url)
                            toaster.toast(copiedText)
                        }
                    }
                },
            ) {
                Icon(Icons.Rounded.ArrowOutward, contentDescription = openLinkText)
            }
        }
        val browseFile = @Composable { url: Path ->
            if (fileRevealer == null) {

            } else {
                IconButton(
                    {
                        scope.launch {
                            if (!fileRevealer.revealFile(url)) {
                                toaster.toast(
                                    getString(
                                        Lang.cache_details_open_file_failed,
                                        url.inSystem.absolutePath,
                                    ),
                                )
                            }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = browseFileText)
                }
            }
        }
        val placeholderLeadingContent = @Composable { Spacer(Modifier.size(24.dp)) }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListItem(
                headlineContent = {
                    SelectionContainer {
                        Text(
                            details.originalTitle,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                trailingContent = { copyContent { details.originalTitle } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_episode_range)) },
                leadingContent = { Icon(Icons.Rounded.Layers, contentDescription = null) },
                supportingContent = {
                    val range = details.episodeRange
                    SelectionContainer {
                        Text(
                            when {
                                range == null -> unknownText
                                range.isSingleEpisode() -> range.knownSorts.firstOrNull().toString()
                                else -> range.toString()
                            },
                        )
                    }
                },
            )
        }
        if (showSourceInfo) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.cache_details_source)) },
                    leadingContent = { MediaSourceIcon(details.sourceInfo, Modifier.size(24.dp)) },
                    supportingContent = {
                        val kind = when (details.kind) {
                            MediaSourceKind.WEB -> stringResource(Lang.cache_details_source_online)
                            MediaSourceKind.LocalCache -> stringResource(Lang.cache_details_source_local)
                        }
                        SelectionContainer {
                            Text("[$kind] ${details.sourceInfo?.displayName ?: unknownText}")
                        }
                    },
                    trailingContent = run {
                        val originalUrl by rememberUpdatedState(details.originalUrl)
                        if (details.isUrlLegal) {
                            {
                                browseContent(originalUrl)
                            }
                        } else {
                            {
                                copyContent { originalUrl }
                            }
                        }
                    },
                )
            }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_subtitle_group)) },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(details.properties.alliance) } },
                trailingContent = { copyContent { details.properties.alliance } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_subtitle_language)) },
                leadingContent = { Icon(Icons.Rounded.Subtitles, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        Text(
                            remember(details, mediaDetailsStrings) {
                                MediaDetailsRenderer.renderSubtitleLanguages(
                                    details.properties.subtitleKind,
                                    details.properties.subtitleLanguageIds,
                                    mediaDetailsStrings,
                                )
                            },
                        )
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_publish_time)) },
                leadingContent = { Icon(Icons.Rounded.Event, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(formatDateTime(details.publishedTimeMillis)) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_resolution)) },
                leadingContent = { Icon(Icons.Outlined.Hd, contentDescription = null) },
                supportingContent = { SelectionContainer { Text(details.properties.resolution) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_file_size)) },
                leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) },
                supportingContent = {
                    SelectionContainer {
                        if (details.fileSize == FileSize.Unspecified) {
                            Text(unknownText)
                        } else {
                            Text(details.fileSize.toString())
                        }
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(Lang.cache_details_original_link)) },
                leadingContent = placeholderLeadingContent,
                supportingContent = {
                    SelectionContainer {
                        Text(details.contentOriginalUri, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                trailingContent = { copyContent { details.contentOriginalUri } },
            )
        }
        if (details.fileType != null) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.cache_details_file_type)) },
                    leadingContent = placeholderLeadingContent,
                    supportingContent = {
                        SelectionContainer {
                            Text(
                                when (details.fileType) {
                                    ResourceLocation.LocalFile.FileType.MPTS -> "MPTS"
                                    ResourceLocation.LocalFile.FileType.CONTAINED -> "Contained"
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                )
            }
        }
        if (details.contentDownloadUri != null) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.cache_details_original_download_link)) },
                    leadingContent = { Icon(Icons.Rounded.VideoFile, contentDescription = null) },
                    supportingContent = {
                        SelectionContainer {
                            Text(details.contentDownloadUri, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    trailingContent = { copyContent { details.contentDownloadUri } },
                )
            }
        }
        if (details.localCacheFilePath != null) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Lang.cache_details_local_cache_path)) },
                    leadingContent = { Icon(Icons.Rounded.VideoFile, contentDescription = null) },
                    supportingContent = {
                        SelectionContainer {
                            Text(details.localCacheFilePath.toString(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    trailingContent = {
                        browseFile(details.localCacheFilePath)
                    },
                )
            }
        }
        if (downloader != null) {
            downloaderDetailsItems(downloader, unknownText)
        }
        details.extraFiles.subtitles.forEachIndexed { index, subtitle ->
            item {
                ListItem(
                    headlineContent = {
                        SelectionContainer {
                            Text(
                                buildString {
                                    append(stringResource(Lang.cache_details_external_subtitle, index + 1))
                                    subtitle.language?.let {
                                        append(": ")
                                        append(it)
                                    }
                                },
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.FilePresent, contentDescription = null) },
                    supportingContent = { SelectionContainer { Text(subtitle.uri) } },
                    trailingContent = { browseContent(subtitle.uri) },
                )
            }
        }
    }
}

@TestOnly
val TestMediaDetails
    get() = MediaDetails.from(
        TestMediaList[0],
        MediaSourceInfo("test"),
        null,
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewCacheGroupDetailsColumn() = ProvideCompositionLocalsForPreview {
    MediaDetailsLazyGrid(TestMediaDetails)
}
