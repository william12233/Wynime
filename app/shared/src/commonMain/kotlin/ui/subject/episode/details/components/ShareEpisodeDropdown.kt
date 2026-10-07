package com.wynime.app.ui.subject.episode.details.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.navigation.rememberAsyncBrowserNavigator
import com.wynime.app.ui.episode.share.MediaShareData
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_episode_share_copy_link
import com.wynime.app.ui.lang.subject_episode_share_copy_source_page
import com.wynime.app.ui.lang.subject_episode_share_local_file_link
import com.wynime.app.ui.lang.subject_episode_share_open_link
import com.wynime.app.ui.lang.subject_episode_share_open_source_page
import com.wynime.app.ui.lang.subject_episode_share_open_with_other_app
import com.wynime.app.ui.lang.subject_episode_share_stream_link
import com.wynime.app.ui.lang.subject_episode_share_webpage_link
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.platform.isAndroid
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShareEpisodeDropdown(
    data: MediaShareData,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberAsyncHandler()
    val uriHandler = LocalUriHandler.current
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        data.download?.let { download ->
            val downloadText = when (download) {
                is ResourceLocation.HttpStreamingFile -> stringResource(Lang.subject_episode_share_stream_link)
                is ResourceLocation.LocalFile -> stringResource(Lang.subject_episode_share_local_file_link)
                is ResourceLocation.SourcePluginMedia -> stringResource(Lang.subject_episode_share_webpage_link)
                is ResourceLocation.WebVideo -> stringResource(Lang.subject_episode_share_webpage_link)
            }
            val copyDownloadText = stringResource(Lang.subject_episode_share_copy_link, downloadText)
            val openDownloadText = stringResource(Lang.subject_episode_share_open_link, downloadText)
            val openWithOtherAppText = stringResource(Lang.subject_episode_share_open_with_other_app)
            DropdownMenuItem(
                text = {
                    Text(copyDownloadText)
                },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        clipboard.setClipEntryText(download.uri)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            )
            DropdownMenuItem(
                text = { Text(openDownloadText) },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(download.uri)
                },
                leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
            )
            if (LocalPlatform.current.isAndroid() &&
                download !is ResourceLocation.WebVideo &&
                download !is ResourceLocation.SourcePluginMedia
            ) {
                DropdownMenuItem(
                    text = { Text(openWithOtherAppText) },
                    onClick = {
                        onDismissRequest()
                        browserNavigator.intentOpenVideo(context, download.uri)
                    },
                    leadingIcon = { Icon(Icons.Rounded.Outbox, null) },
                )
            }
        }

        data.websiteUrl?.let { websiteUrl ->
            val copySourcePageText = stringResource(Lang.subject_episode_share_copy_source_page)
            val openSourcePageText = stringResource(Lang.subject_episode_share_open_source_page)
            DropdownMenuItem(
                text = { Text(copySourcePageText) },
                onClick = {
                    onDismissRequest()
                    scope.launch {
                        clipboard.setClipEntryText(websiteUrl)
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            )
            DropdownMenuItem(
                text = { Text(openSourcePageText) },
                onClick = {
                    onDismissRequest()
                    uriHandler.openUri(websiteUrl)
                },
                leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) },
            )
        }
    }
}
