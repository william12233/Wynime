package com.wynime.app.ui.comment

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.interaction.rememberImeMaxHeight
import com.wynime.app.ui.foundation.widgets.ModalBottomImeAwareSheet
import com.wynime.app.ui.foundation.widgets.rememberModalBottomImeAwareSheetState

@Composable
fun EditCommentSheet(
    state: CommentEditorState,
    onDismiss: () -> Unit,
    onSendComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomImeAwareSheetState()

    val contentPadding = 16.dp
    val imePresentMaxHeight by rememberImeMaxHeight()

    ModalBottomImeAwareSheet(
        state = sheetState,
        onDismiss = onDismiss,
        modifier = Modifier
            .navigationBarsPadding()
            .ifThen(!state.showStickerPanel) { imePadding() },
    ) {
        EditComment(
            state = state,
            onCloseRequest = onDismiss,
            modifier = modifier
                .ifThen(state.editExpanded) { statusBarsPadding() }
                .ifThen(!state.editExpanded) { padding(top = contentPadding) }
                .padding(contentPadding),
            stickerPanelHeight = with(density) { imePresentMaxHeight.toDp() },
            focusRequester = focusRequester,
            onSendComplete = {
                sheetState.close()
                onSendComplete()
            },
        )
    }
}
