package com.wynime.app.ui.richtext

import androidx.compose.ui.text.style.TextAlign
import com.wynime.utils.bbcode.BBCode
import kotlin.test.Test
import kotlin.test.assertEquals

class BBCodeAlignTest {
    private fun parse(code: String) = BBCode.parse(code).toUIRichElements()

    private fun assertAnnotatedText(
        element: UIRichElement,
        content: String,
        align: TextAlign = TextAlign.Unspecified,
    ) {
        val annotatedText = element as? UIRichElement.AnnotatedText
            ?: error("expected AnnotatedText but was $element")
        assertEquals(
            content,
            annotatedText.slice.filterIsInstance<UIRichElement.Annotated.Text>().joinToString("") { it.content },
        )
        assertEquals(align, annotatedText.align)
    }

    @Test
    fun `no align tag keeps text unspecified`() {
        val elements = parse("Hello World!")
        assertEquals(1, elements.size)
        assertAnnotatedText(elements[0], "Hello World!")
    }

    @Test
    fun `center tag aligns text to center`() {
        val elements = parse("[center]Hello World![/center]")
        assertEquals(1, elements.size)
        assertAnnotatedText(elements[0], "Hello World!", TextAlign.Center)
    }

    @Test
    fun `left and right tags are mapped`() {
        assertAnnotatedText(parse("[left]Hello![/left]").single(), "Hello!", TextAlign.Left)
        assertAnnotatedText(parse("[right]Hello![/right]").single(), "Hello!", TextAlign.Right)
    }

    @Test
    fun `align is a paragraph level property so text is split`() {
        val elements = parse("Hello [center]World![/center] Again!")
        assertEquals(3, elements.size)
        assertAnnotatedText(elements[0], "Hello ")
        assertAnnotatedText(elements[1], "World!", TextAlign.Center)
        assertAnnotatedText(elements[2], " Again!")
    }

    @Test
    fun `same align is merged into one paragraph`() {
        val elements = parse("[center]Hello [b]World![/b][/center]")
        assertEquals(1, elements.size)
        assertAnnotatedText(elements[0], "Hello World!", TextAlign.Center)
    }

    @Test
    fun `nested align overrides outer align`() {
        val elements = parse("[center]Hello[left]World[/left]![/center]")
        assertEquals(3, elements.size)
        assertAnnotatedText(elements[0], "Hello", TextAlign.Center)
        assertAnnotatedText(elements[1], "World", TextAlign.Left)
        assertAnnotatedText(elements[2], "!", TextAlign.Center)
    }

    @Test
    fun `image splits the paragraph and align is restored after it`() {
        val elements = parse("[center]Hello[img]https://example.com/image.png[/img]World[/center]")
        assertEquals(3, elements.size)
        assertAnnotatedText(elements[0], "Hello", TextAlign.Center)
        assertEquals(UIRichElement.Image("https://example.com/image.png", null), elements[1])
        assertAnnotatedText(elements[2], "World", TextAlign.Center)
    }
}
