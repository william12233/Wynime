package com.wynime.app.ui.subject.details.sections

import com.wynime.app.ui.comment.UIRichText
import com.wynime.app.ui.richtext.UIRichElement

internal fun UIRichText.toPlainText(): String =
    elements.asSequence()
        .filterIsInstance<UIRichElement.AnnotatedText>()
        .flatMap { it.slice }
        .filterIsInstance<UIRichElement.Annotated.Text>()
        .joinToString("") { it.content }
        .trim()
