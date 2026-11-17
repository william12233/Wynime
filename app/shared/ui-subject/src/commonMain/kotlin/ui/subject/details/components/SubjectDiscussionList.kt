package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.subject.SubjectDiscussion
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.tools.formatDateTime
import com.wynime.app.ui.comment.CommentDefaults
import com.wynime.app.ui.comment.CommentItemDefaults
import com.wynime.app.ui.comment.CommentItemLayout
import com.wynime.app.ui.comment.CommentMapperContext
import com.wynime.app.ui.foundation.ImageViewer
import com.wynime.app.ui.foundation.ImageViewerBackHandler
import com.wynime.app.ui.foundation.rememberImageViewerHandler
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_open_in_bangumi
import com.wynime.app.ui.lang.subject_details_discussions_empty
import com.wynime.app.ui.lang.subject_details_discussions_unavailable
import com.wynime.app.ui.lang.subject_details_tab_discussions
import com.wynime.app.ui.lang.subject_episode_close
import com.wynime.app.ui.richtext.RichText
import com.wynime.app.ui.search.LoadErrorCard
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubjectDiscussionDialog(
    discussions: Flow<PagingData<SubjectDiscussion>>,
    onClickUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val imageViewer = rememberImageViewerHandler()
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImageViewerBackHandler(imageViewer)
        Surface(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Lang.subject_details_tab_discussions), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(Lang.subject_episode_close))
                    }
                }
                SubjectDiscussionList(
                    discussions,
                    onClickUrl = onClickUrl,
                    onClickImage = { imageViewer.viewImage(it) },
                    contentPadding = PaddingValues(bottom = 16.dp),
                )
            }
        }
        ImageViewer(imageViewer) { imageViewer.clear() }
    }
}

@Composable
internal fun SubjectDiscussionList(
    discussions: Flow<PagingData<SubjectDiscussion>>,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val items = discussions.collectAsLazyPagingItemsWithLifecycle()

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        items(items.itemCount) { index ->
            val discussion = items[index] ?: return@items
            SubjectDiscussionItem(discussion, onClickUrl, onClickImage)
            if (index < items.itemCount - 1) HorizontalDivider()
        }

        val loadState = if (items.itemCount == 0) items.loadState.refresh else items.loadState.append
        when {
            loadState is LoadState.Error -> item {
                LoadErrorCard(
                    LoadError.fromException(loadState.error),
                    onRetry = items::retry,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }

            loadState is LoadState.Loading ||
                (items.itemCount == 0 && items.loadState.append.endOfPaginationReached) -> item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadState is LoadState.Loading) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(Lang.subject_details_discussions_empty))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectDiscussionItem(
    discussion: SubjectDiscussion,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
) {
    val openInBangumi = stringResource(Lang.comment_open_in_bangumi)
    val authorName = discussion.author.nickname?.takeIf(String::isNotBlank)
        ?: discussion.author.username?.takeIf(String::isNotBlank)
        ?: discussion.author.id
    CommentItemLayout(
        avatar = { CommentDefaults.Avatar(discussion.author.avatarUrl) },
        title = {
            Text(
                authorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    discussion.title,
                    Modifier.clickable(onClickLabel = openInBangumi) { onClickUrl(discussion.originalUrl) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val content = discussion.content
                if (content == null) {
                    Text(
                        stringResource(Lang.subject_details_discussions_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val elements = remember(content) {
                        val bbcode = bangumiPhoto.replace(content) { match ->
                            "[img]https://lain.bgm.tv/pic/photo/l/${match.groupValues[1]}[/img]"
                        }
                        runCatching { CommentMapperContext.parseBBCode(bbcode).elements }.getOrDefault(emptyList())
                    }
                    SelectionContainer {
                        if (elements.isEmpty()) {
                            Text(content, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            RichText(
                                elements,
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                onClickUrl = onClickUrl,
                                onClickImage = onClickImage,
                            )
                        }
                    }
                }
            }
        },
        timestamp = {
            Text(
                "${formatDateTime(discussion.createdAtMillis)} · Bangumi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.fillMaxWidth().padding(CommentItemDefaults.ContentPadding),
    )
}

private val bangumiPhoto = Regex("""\[photo=\d+]([a-f\d]{2}/[a-f\d]{2}/[\w.-]+)\[/photo]""", RegexOption.IGNORE_CASE)
