package com.wynime.app.ui.subject.episode.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.domain.comment.CommentSendResult
import com.wynime.app.ui.comment.CommentEditorState
import com.wynime.app.ui.comment.CommentMapperContext
import com.wynime.app.ui.comment.EditComment
import com.wynime.app.ui.comment.EditCommentSheet
import com.wynime.app.ui.comment.EditCommentDefaults
import com.wynime.app.ui.comment.EditCommentSticker
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.rememberBackgroundScope

@Composable
fun EpisodeEditCommentSheet(
    state: CommentEditorState,
    onDismiss: () -> Unit,
    onSendComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditCommentSheet(
        state = state,
        onDismiss = onDismiss,
        onSendComplete = onSendComplete,
        modifier = modifier,
    )
}

@Preview
@Composable
fun PreviewEditComment() {
    ProvideCompositionLocalsForPreview {
        val scope = rememberBackgroundScope()
        EditComment(
            state = remember {
                CommentEditorState(
                    showExpandEditCommentButton = true,
                    initialEditExpanded = false,
                    panelTitle = mutableStateOf("评论：我心里危险的东西 第二季"),
                    stickers = mutableStateOf(
                        (0..64)
                            .map { EditCommentSticker(it, null) }
                            .toList(),
                    ),
                    onSend = { _, _ -> CommentSendResult.Ok },
                    richTextRenderer = {
                        withContext(Dispatchers.Default) {
                            with(CommentMapperContext) { parseBBCode(it) }
                        }
                    },
                    backgroundScope = scope.backgroundScope,
                )
            },
        )
    }
}

@Preview
@Composable
fun PreviewEditCommentStickerPanel() {
    ProvideCompositionLocalsForPreview {
        EditCommentDefaults.StickerSelector(
            list = (0..64)
                .map { EditCommentSticker(it, null) }
                .toList(),
            onClickItem = { },
        )
    }
}
