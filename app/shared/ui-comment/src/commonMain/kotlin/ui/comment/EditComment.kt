package com.wynime.app.ui.comment

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.wynime.app.domain.comment.CommentSendResult
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.interaction.isImeVisible
import com.wynime.app.ui.foundation.text.ProvideContentColor
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_ani_only_notice
import com.wynime.app.ui.lang.comment_send_failed_network
import com.wynime.app.ui.lang.comment_send_failed_unknown
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditComment(
    state: CommentEditorState,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    stickerPanelHeight: Dp = EditCommentDefaults.MinStickerHeight.dp,
    onSendComplete: () -> Unit = { },
    onCloseRequest: () -> Unit = { },
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val sendingComment by state.sending.collectAsStateWithLifecycle(false)

    val imeVisible = isImeVisible()
    var previousImeVisible by remember { mutableStateOf(false) }
    SideEffect {
        if (!previousImeVisible && imeVisible) {
            state.toggleStickerPanelState(false)
        }
        previousImeVisible = imeVisible
    }

    EditCommentScaffold(
        previewing = state.previewing,
        modifier = modifier,
        title = {
            state.panelTitle?.let { EditCommentDefaults.Title(it) }
        },
        actionRow = {
            EditCommentDefaults.ActionRow(
                sendTarget = state.currentSendTarget,
                previewing = state.previewing,
                sending = sendingComment,
                onClickBold = { state.wrapSelectionWith("[b][/b]", 3) },
                onClickItalic = { state.wrapSelectionWith("[i][/i]", 3) },
                onClickUnderlined = { state.wrapSelectionWith("[u][/u]", 3) },
                onClickStrikethrough = { state.wrapSelectionWith("[s][/s]", 3) },
                onClickMask = { state.wrapSelectionWith("[mask][/mask]", 6) },
                onClickImage = { state.wrapSelectionWith("[img][/img]", 5) },
                onClickUrl = { state.wrapSelectionWith("[url=][/url]", 5) },
                onClickEmoji = {
                    state.toggleStickerPanelState()
                    if (state.showStickerPanel) keyboard?.hide()
                },
                onPreview = {
                    keyboard?.hide()
                    state.toggleStickerPanelState(false)
                    state.togglePreview()
                },
                onSend = {
                    keyboard?.hide()
                    state.toggleStickerPanelState(false)
                    scope.launch {
                        if (state.send()) onSendComplete()
                    }
                },
            )

            if (state.showStickerPanel) {
                EditCommentDefaults.StickerSelector(
                    list = state.stickers,
                    modifier = Modifier.fillMaxWidth()
                        .height(max(EditCommentDefaults.MinStickerHeight.dp, stickerPanelHeight)),
                    onClickItem = { stickerId ->
                        val inserted = "(bgm$stickerId)"
                        state.insertTextAt(inserted, inserted.length)
                    },
                )
            }
        },
        expanded = state.expandButtonState,
        onClickExpand = { state.editExpanded = it },
        onClickClose = onCloseRequest,
    ) { previewing ->
        Column {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                if (previewing) {
                    LaunchedEffect(Unit) { state.renderPreview() }
                    EditCommentDefaults.Preview(
                        content = state.previewContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .ifThen(state.editExpanded) { fillMaxHeight() }
                            .animateContentSize(),
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .ifThen(state.editExpanded) { fillMaxHeight() }
                            .animateContentSize(),
                    ) {
                        EditCommentDefaults.CommentTextField(
                            value = state.content,
                            enabled = !sendingComment,
                            maxLines = if (state.editExpanded) Int.MAX_VALUE else 3,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .fillMaxWidth()
                                .ifThen(state.editExpanded) { fillMaxHeight() },
                            onValueChange = { state.setContent(it) },
                            interactionSource = remember { MutableInteractionSource() },
                        )

                        EditCommentDefaults.ActionButton(
                            imageVector = if (state.editExpanded)
                                Icons.Default.CloseFullscreen
                            else
                                Icons.Default.OpenInFull,
                            enabled = true,
                            onClick = { state.editExpanded = !state.editExpanded },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                        )
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
                WynimeAnimatedVisibility(
                    visible = state.sendResult is CommentSendResult.Error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    val sendErrorText = when (val sendResult = state.sendResult) {
                        is CommentSendResult.Error -> renderCommentSendError(sendResult)
                        else -> ""
                    }
                    Text(
                        text = sendErrorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun renderCommentSendError(result: CommentSendResult.Error): String {
    return when (result) {
        CommentSendResult.NetworkError -> stringResource(Lang.comment_send_failed_network)
        is CommentSendResult.UnknownError -> stringResource(Lang.comment_send_failed_unknown, result.message)
    }
}

@Composable
fun EditCommentScaffold(
    previewing: Boolean,
    actionRow: @Composable ColumnScope.() -> Unit,
    onClickExpand: (Boolean) -> Unit,
    onClickClose: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    title: (@Composable () -> Unit)? = null,
    contentColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.(previewing: Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title != null) {
                Box(Modifier.weight(1.0f)) {
                    title()
                }
            }
            if (expanded != null) {
                EditCommentDefaults.ActionButton(
                    imageVector = Icons.Default.Close,
                    enabled = true,
                    onClick = onClickClose,
                )
            }
        }

        Row(Modifier.padding(horizontal = 8.dp)) {
            Text(
                stringResource(Lang.comment_ani_only_notice),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        ProvideContentColor(contentColor) {
            Crossfade(
                targetState = previewing,
                modifier = Modifier.weight(1.0f, fill = false),
            ) { previewing ->
                content(previewing)
            }

        }

        Column {
            actionRow()
        }

    }
}
