package com.wynime.app.ui.foundation.richtext

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.richtext.RichText
import com.wynime.app.ui.richtext.UIRichElement

@Composable
private fun PreviewImpl() {
    RichText(
        elements = listOf(
            UIRichElement.AnnotatedText(
                slice = listOf(
                    UIRichElement.Annotated.Text("hello", 16f),
                    UIRichElement.Annotated.Text("my italic content", 16f, italic = true),
                    UIRichElement.Annotated.Text("my bold content", 16f, bold = true),
                    UIRichElement.Annotated.Text("my underline content", 16f, underline = true),
                    UIRichElement.Annotated.Text("\nmy mask content\n", 16f, mask = true),
                    UIRichElement.Annotated.Text("my strikethrough content", 16f, strikethrough = true),
                    UIRichElement.Annotated.Text(
                        "my combined content",
                        16f,
                        bold = true,
                        underline = true,
                        strikethrough = true,
                    ),
                    UIRichElement.Annotated.Text("have link", 16f, url = "https://localhost"),
                    UIRichElement.Annotated.Text(
                        "have link combined",
                        16f,
                        bold = true,
                        strikethrough = true,
                        url = "https://localhost",
                    ),
                    UIRichElement.Annotated.Sticker(
                        "sticker_1",
                        null,
                        "",
                    ),
                ),
            ),
            UIRichElement.AnnotatedText(
                slice = listOf(UIRichElement.Annotated.Text("my centered content", 16f)),
                align = TextAlign.Center,
            ),
            UIRichElement.AnnotatedText(
                slice = listOf(UIRichElement.Annotated.Text("my right aligned content", 16f)),
                align = TextAlign.Right,
            ),
            UIRichElement.AnnotatedText(
                slice = listOf(UIRichElement.Annotated.Text("my left aligned content", 16f)),
                align = TextAlign.Left,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 36.dp),
    )
}

@PreviewLightDark
@Composable
private fun PreviewRichTextSurfaceContainer() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            PreviewImpl()
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewRichTextSurface() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PreviewImpl()
        }
    }
}
