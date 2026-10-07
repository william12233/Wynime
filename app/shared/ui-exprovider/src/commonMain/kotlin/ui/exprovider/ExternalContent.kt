package com.wynime.app.ui.exprovider

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import com.wynime.app.platform.LocalContext

@Composable
fun ExternalContent(
    contentId: String,
    modifier: Modifier = Modifier,
) {
    val provider = LocalExternalContentProvider.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(provider) {
        scope.launch { provider.initialize(context, contentId) }
        onDispose { provider.dispose(contentId) }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

        ExternalContentImpl(provider, contentId, widthPx, heightPx, Modifier.fillMaxSize())
    }
}

@Composable
internal expect fun BoxWithConstraintsScope.ExternalContentImpl(
    provider: ExternalContentProvider,
    contentId: String,
    expectedWidth: Int,
    expectedHeight: Int,
    modifier: Modifier = Modifier,
)