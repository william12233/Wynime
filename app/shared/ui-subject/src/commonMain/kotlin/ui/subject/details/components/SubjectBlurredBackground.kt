package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.backgroundWithGradient
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium

@Composable
fun SubjectBlurredBackground(
    coverImageUrl: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
) {
    Box(
        modifier
            .blur(if (currentWindowAdaptiveInfo1().isWidthAtLeastMedium) 32.dp else 16.dp)
            .backgroundWithGradient(
                coverImageUrl, backgroundColor,
                brush = if (backgroundColor.luminance() < 0.5f) {
                    Brush.verticalGradient(
                        0f to surfaceColor.copy(alpha = 0xA2.toFloat() / 0xFF),
                        0.4f to surfaceColor.copy(alpha = 0xA2.toFloat() / 0xFF),
                        1.00f to backgroundColor,
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color(0xA2FAFAFA),
                        0.4f to Color(0xA2FAFAFA),
                        1.00f to backgroundColor,
                    )
                },
            ),
    )
}

@Composable
@Preview
fun PreviewSubjectBlurredBackground() {

    ProvideCompositionLocalsForPreview {
        SubjectBlurredBackground(
            coverImageUrl = "https://ui-avatars.com/api/?name=John+Doe",
            Modifier
                .height(270.dp)
                .fillMaxWidth(),
        )
    }
}
