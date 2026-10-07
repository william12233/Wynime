package com.wynime.utils.bbcode

sealed interface RichElement {

    val jumpUrl: String?

    data class Text(
        val value: String,
        val size: Int = DEFAULT_SIZE,

        val color: String? = null,

        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val bold: Boolean = false,

        val mask: Boolean = false,
        val code: Boolean = false,

        val align: Align = DEFAULT_ALIGN,

        override val jumpUrl: String? = null
    ) : RichElement {

        enum class Align {

            DEFAULT,
            LEFT,
            CENTER,
            RIGHT,
        }

        companion object {
            const val DEFAULT_SIZE = 16
            val DEFAULT_ALIGN = Align.DEFAULT
        }
    }

    data class Image(
        val imageUrl: String,
        val width: Int? = null,
        val height: Int? = null,
        override val jumpUrl: String? = null
    ) : RichElement

    data class Quote(
        val contents: RichText,
        override val jumpUrl: String? = null
    ) : RichElement

    data class BangumiSticker(
        val id: Int,
        override val jumpUrl: String? = null
    ) : RichElement

    data class Kanmoji(
        val id: String,
        override val jumpUrl: String? = null
    ) : RichElement
}