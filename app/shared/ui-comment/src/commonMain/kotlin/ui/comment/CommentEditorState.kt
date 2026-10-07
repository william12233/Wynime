package com.wynime.app.ui.comment

import androidx.annotation.UiThread
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.wynime.app.domain.comment.CommentContext
import com.wynime.app.domain.comment.CommentSendResult
import com.wynime.app.tools.MonoTasker
import org.jetbrains.compose.resources.DrawableResource
import kotlin.coroutines.CoroutineContext

@Stable
class CommentEditorState(
    showExpandEditCommentButton: Boolean,
    initialEditExpanded: Boolean,
    panelTitle: State<String?>,
    stickers: State<List<EditCommentSticker>>,
    private val richTextRenderer: suspend (String) -> UIRichText,
    private val onSend: suspend (target: CommentContext, content: String) -> CommentSendResult,
    backgroundScope: CoroutineScope,
) {
    private val editor = CommentEditorTextState("")

    private val sendTasker = MonoTasker(backgroundScope)

    val panelTitle by panelTitle

    var currentSendTarget: CommentContext? by mutableStateOf(null)
        private set
    val sending get() = sendTasker.isRunning

    val content get() = editor.textField
    var previewing by mutableStateOf(false)
        private set
    var previewContent: UIRichText? by mutableStateOf(null)
        private set

    var editExpanded: Boolean by mutableStateOf(initialEditExpanded)
    val expandButtonState by derivedStateOf { if (!showExpandEditCommentButton) null else editExpanded }

    var showStickerPanel: Boolean by mutableStateOf(false)
        private set
    val stickers by stickers

    var sendResult: CommentSendResult? by mutableStateOf(null)
        private set

    fun startEdit(newTarget: CommentContext) {
        if (newTarget != currentSendTarget) {
            editor.override(TextFieldValue(""))
        }
        currentSendTarget = newTarget
        previewing = false
        previewContent = null
        editExpanded = false
    }

    fun toggleStickerPanelState(desired: Boolean? = null) {
        showStickerPanel = desired ?: !showStickerPanel
    }

    fun setContent(value: TextFieldValue) {
        editor.override(value)
    }

    fun wrapSelectionWith(value: String, secondSliceIndex: Int) {
        editor.wrapSelectionWith(value, secondSliceIndex)
    }

    fun insertTextAt(value: String, cursorOffset: Int = value.length) {
        editor.insertTextAt(value, cursorOffset)
    }

    fun togglePreview() {
        previewing = !previewing
    }

    @UiThread
    suspend fun renderPreview() {
        previewContent = null
        val rendered = richTextRenderer(content.text)
        previewContent = rendered
    }

    suspend fun send(
        context: CoroutineContext = Dispatchers.Default
    ): Boolean {
        val target = currentSendTarget
        val content = editor.textField.text

        editExpanded = false
        sendResult = null

        val result = sendTasker.async(context) {
            checkNotNull(target)
            onSend(target, content)
        }.await()

        return (result is CommentSendResult.Ok).also {
            if (it) {
                editor.override(TextFieldValue(""))
            } else {
                sendResult = result
            }
        }
    }

    fun cancelSend() {
        sendTasker.cancel()
    }
}

@Immutable
data class EditCommentSticker(
    val id: Int,
    val drawableRes: DrawableResource?,
)