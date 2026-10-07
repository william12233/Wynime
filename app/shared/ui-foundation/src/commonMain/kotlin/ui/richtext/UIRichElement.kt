package com.wynime.app.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.DrawableResource

sealed interface UIRichElement {
    sealed interface Annotated {
        val url: String?

        data class Text(
            val content: String,
            val size: Float = RichTextDefaults.FontSize,
            val color: Color = Color.Unspecified,

            val italic: Boolean = false,
            val underline: Boolean = false,
            val strikethrough: Boolean = false,
            val bold: Boolean = false,

            val mask: Boolean = false,
            val code: Boolean = false,

            override val url: String? = null
        ) : Annotated

        data class Sticker(
            val id: String,
            val resource: DrawableResource?,
            override val url: String? = null
        ) : Annotated
    }

    data class AnnotatedText(
        val slice: List<Annotated>,
        val maxLine: Int? = null,
        val align: TextAlign = TextAlign.Unspecified,
    ) : UIRichElement

    data class Quote(val content: List<UIRichElement>) : UIRichElement

    data class Image(val imageUrl: String, val jumpUrl: String?) : UIRichElement
}
