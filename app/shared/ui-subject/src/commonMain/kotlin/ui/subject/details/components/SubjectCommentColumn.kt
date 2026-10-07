package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.wynime.app.ui.comment.UIComment
import com.wynime.app.ui.comment.UICommentSource
import com.wynime.app.ui.comment.generateUiComment
import com.wynime.app.ui.comment.rememberTestCommentState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.layout.ConnectedScrollState
import com.wynime.app.ui.foundation.layout.rememberConnectedScrollState
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.utils.platform.annotations.TestOnly

@Composable
fun SubjectDetailsDefaults.SubjectCommentColumn(
    state: CommentState,
    onClickUrl: (url: String) -> Unit,
    onClickImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    reportState: CommentReportState? = null,
    onOpenOriginal: ((UIComment) -> Unit)? = null,
    onClickReply: ((UIComment) -> Unit)? = null,
    onToggleReaction: ((UIComment, String) -> Unit)? = null,
    showRating: Boolean = true,
    connectedScrollState: ConnectedScrollState? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pullToRefreshEnabled: Boolean = true,
) {
    val toaster = LocalToaster.current
    LaunchedEffect(state) {
        state.actionSubmitFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        val items = state.list.collectAsLazyPagingItemsWithLifecycle()
        CommentOverlayCleanupEffect(state, items)
        CommentColumn(
            items,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(align = Alignment.CenterHorizontally)
                .widthIn(max = SubjectDetailsDefaults.MaximumContentWidth)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            state = gridState,
            connectedScrollState = connectedScrollState,
            pullToRefreshEnabled = pullToRefreshEnabled,
        ) { _, comment ->
            val commentWithOverlay = state.withOverlay(comment)
            CommentItem(
                comment = commentWithOverlay,
                onClickUrl = onClickUrl,
                onClickImage = onClickImage,
                showRating = showRating,
                onClickReply = onClickReply,
                onToggleVote = { c, vote -> state.toggleVote(c, vote) },
                onToggleReaction = if (commentWithOverlay.source == UICommentSource.WYNIME) onToggleReaction else null,
                menu = CommentMenuHandlers(
                    onOpenOriginal = if (commentWithOverlay.source == UICommentSource.BANGUMI) {
                        onOpenOriginal
                    } else null,

                    onReport = if (commentWithOverlay.source == UICommentSource.WYNIME) {
                        reportState?.let { report -> { report.show(it) } }
                    } else null,
                ),
            )
        }
    }
}

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewSubjectCommentColumn() {
    ProvideCompositionLocalsForPreview {
        Surface {
            SubjectDetailsDefaults.SubjectCommentColumn(
                state = rememberTestCommentState(generateUiComment(4)),
                onClickUrl = { },
                onClickImage = {},
                connectedScrollState = rememberConnectedScrollState(),
            )
        }
    }
}
