package com.wynime.app.ui.subject.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.person.PersonCommentTarget
import com.wynime.app.domain.comment.CommentContext
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.comment.CommentEditorState
import com.wynime.app.ui.comment.CommentReportHost
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError

@Stable
class PeopleCommentsState(
    val target: PersonCommentTarget,
    val commentState: CommentState,
    val reportState: CommentReportState,
    val editorState: CommentEditorState,

    val originalCommentsUrl: String,
    private val onRefresh: () -> Unit,
) {

    fun refresh() = onRefresh()

    fun startNewComment() = editorState.startEdit(CommentContext.PersonComment(target))

    fun startReply(commentId: String) = editorState.startEdit(CommentContext.PersonCommentReply(target, commentId))
}

@Composable
internal fun PeopleCommentsHost(
    comments: PeopleCommentsState,
    showAllComments: Boolean,
    onDismissAllComments: () -> Unit,
) {
    val toaster = LocalToaster.current
    LaunchedEffect(comments) {
        comments.commentState.commentLoadFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }
    CommentReportHost(comments.reportState)
    if (showAllComments) {
        PersonCommentsSheet(comments, onDismissRequest = onDismissAllComments)
    }
}
