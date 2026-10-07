package com.wynime.app.ui.exprovider

import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal actual fun BoxWithConstraintsScope.ExternalContentImpl(
    provider: ExternalContentProvider,
    contentId: String,
    expectedWidth: Int,
    expectedHeight: Int,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context ->
            provider.viewProvider(context, contentId, expectedWidth, expectedHeight) as View
        },
        modifier = Modifier.fillMaxSize(),
        update = { _ -> },
    )
}