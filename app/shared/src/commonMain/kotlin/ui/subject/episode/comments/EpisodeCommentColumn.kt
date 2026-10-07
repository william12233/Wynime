@file:OptIn(TestOnly::class)

package com.wynime.app.ui.subject.episode.comments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.comment.CommentColumn
import com.wynime.app.ui.comment.CommentItem
import com.wynime.app.ui.comment.CommentMenuHandlers
import com.wynime.app.ui.comment.CommentOverlayCleanupEffect
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.comment.UICommentSource
import com.wynime.app.ui.comment.generateUiComment
import com.wynime.app.ui.comment.rememberTestCommentState
import com.wynime.app.ui.foundation.LocalImageViewerHandler
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_send_comment
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun EpisodeCommentColumn(
    state: CommentState,
    episodeId: Int,
    onClickReply: (commentId: String) -> Unit,
    onNewCommentClick: () -> Unit,
    onClickUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    reportState: CommentReportState? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val imageViewer = LocalImageViewerHandler.current
    val uriHandler = LocalUriHandler.current
    val writeCommentText = stringResource(Lang.comment_send_comment)
    val toaster = LocalToaster.current
    LaunchedEffect(state) {
        state.actionSubmitFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }
    LaunchedEffect(state) {
        state.commentLoadFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }

    Scaffold(
        modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(writeCommentText) },
                icon = {
                    Icon(Icons.Rounded.AddComment, null)
                },
                onClick = onNewCommentClick,
                expanded = !gridState.canScrollBackward,
            )
        },
    ) { _ ->
        val items = state.list.collectAsLazyPagingItemsWithLifecycle()
        CommentOverlayCleanupEffect(state, items)
        CommentColumn(
            items,
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
                .plus(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()),
        ) { _, comment ->
            val commentWithOverlay = state.withOverlay(comment)
            CommentItem(
                comment = commentWithOverlay,
                onClickUrl = onClickUrl,
                onClickImage = { imageViewer.viewImage(it) },
                onClickReply = { onClickReply(it.sourceCommentId) },
                onToggleVote = { c, vote -> state.toggleVote(c, vote) },
                onToggleReaction = if (commentWithOverlay.source == UICommentSource.WYNIME) {
                    { c, value -> state.submitReaction(c, value) }
                } else null,
                menu = CommentMenuHandlers(
                    onOpenOriginal = if (commentWithOverlay.source == UICommentSource.BANGUMI) {
                        { uriHandler.openUri("https://bgm.tv/ep/$episodeId") }
                    } else null,

                    onReport = if (commentWithOverlay.source == UICommentSource.WYNIME) {
                        reportState?.let { report -> { report.show(it) } }
                    } else null,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewEpisodeCommentColumn() {
    ProvideCompositionLocalsForPreview {
        EpisodeCommentColumn(
            state = rememberTestCommentState(commentList = generateUiComment(4)),
            episodeId = 1,
            onClickReply = { },
            onNewCommentClick = { },
            onClickUrl = { },
        )
    }
}
