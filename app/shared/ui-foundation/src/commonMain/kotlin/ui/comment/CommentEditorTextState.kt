package com.wynime.app.ui.comment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

class CommentEditorTextState(
    initialText: String
) {
    var textField by mutableStateOf(TextFieldValue(initialText))
        private set

    fun insertTextAt(
        value: String,
        cursorOffset: Int = value.length
    ) {
        val current = textField
        val currentText = current.text
        val selectionLeft = current.selection.start

        val newText = buildString {
            append(currentText.take(selectionLeft))
            append(value)
            append(currentText.drop(selectionLeft))
        }

        textField = current.copy(
            annotatedString = AnnotatedString(newText),
            selection = TextRange((selectionLeft + cursorOffset).coerceIn(0..newText.lastIndex + 1)),
        )
    }

    fun wrapSelectionWith(
        value: String,
        secondSliceIndex: Int
    ) {
        require(secondSliceIndex in value.indices) {
            "secondSliceIndex is out of bound. value length = ${value.length}, secondSliceIndex = $secondSliceIndex"
        }
        val current = textField
        if (current.selection.length == 0) {
            insertTextAt(value, secondSliceIndex)
            return
        }

        val currentText = current.text
        val selection = current.selection

        val newText = buildString {
            append(currentText.take(selection.start))
            append(value.take(secondSliceIndex))
            append(currentText.substring(selection.start, selection.end))
            append(value.substring(secondSliceIndex, value.length))
            append(currentText.substring(selection.end, currentText.length))
        }

        textField = current.copy(
            annotatedString = AnnotatedString(newText),
            selection = TextRange(
                selection.start + secondSliceIndex,
                selection.start + secondSliceIndex + selection.length,
            ),
        )
    }

    fun override(value: TextFieldValue) {
        textField = value
    }

    companion object {
        val Saver: Saver<CommentEditorTextState, Any> = listSaver(
            save = { original ->
                listOf(
                    with(TextFieldValue.Saver) { this@listSaver.save(original.textField) }
                )
            },
            restore = { saveable ->
                CommentEditorTextState("").apply {
                    override(with(TextFieldValue.Saver) { restore(saveable[0]!!) }!!)
                }
            }
        )
    }
}