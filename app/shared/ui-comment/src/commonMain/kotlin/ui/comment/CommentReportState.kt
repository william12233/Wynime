package com.wynime.app.ui.comment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_report_submitted
import com.wynime.app.data.models.comment.CommentReportReason as DataCommentReportReason
import org.jetbrains.compose.resources.stringResource

@Stable
class CommentReportState(
    private val onSubmitReport: suspend (comment: UIComment, reason: CommentReportReason, detail: String) -> Unit,
    private val backgroundScope: CoroutineScope,
) {

    var target: UIComment? by mutableStateOf(null)
        private set

    private val submitResultChannel = Channel<Result<Unit>>(Channel.BUFFERED)

    val submitResults: Flow<Result<Unit>> = submitResultChannel.receiveAsFlow()

    fun show(comment: UIComment) {
        target = comment
    }

    fun dismiss() {
        target = null
    }

    fun submit(comment: UIComment, reason: CommentReportReason, detail: String) {
        backgroundScope.launch {
            submitResultChannel.trySend(submitAwait(comment, reason, detail))
        }
    }

    suspend fun submitAwait(comment: UIComment, reason: CommentReportReason, detail: String): Result<Unit> = try {
        onSubmitReport(comment, reason, detail)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Composable
fun CommentReportHost(state: CommentReportState) {
    val toaster = LocalToaster.current
    val submittedText = stringResource(Lang.comment_report_submitted)
    LaunchedEffect(state) {
        state.submitResults.collect { result ->
            result.fold(
                onSuccess = { toaster.toast(submittedText) },
                onFailure = { toaster.showLoadError(LoadError.fromException(it)) },
            )
        }
    }
    state.target?.let { target ->
        CommentReportSheet(
            snapshotText = remember(target) { target.reportSnapshotText() },
            onSubmit = { reason, detail ->
                state.submit(target, reason, detail)
                state.dismiss()
            },
            onDismissRequest = { state.dismiss() },
        )
    }
}

fun UIComment.reportSnapshotText(): String {
    val authorName = author?.nickname ?: author?.id ?: ""
    val text = rawContent ?: content.toPlainText()
    return if (authorName.isEmpty()) text else "$authorName：$text"
}

fun CommentReportReason.toDataReason(): DataCommentReportReason = when (this) {
    CommentReportReason.SPAM -> DataCommentReportReason.SPAM
    CommentReportReason.HARASSMENT -> DataCommentReportReason.HARASSMENT
    CommentReportReason.SPOILER -> DataCommentReportReason.SPOILER
    CommentReportReason.NSFW -> DataCommentReportReason.NSFW
    CommentReportReason.ILLEGAL -> DataCommentReportReason.ILLEGAL
    CommentReportReason.OTHER -> DataCommentReportReason.OTHER
}
