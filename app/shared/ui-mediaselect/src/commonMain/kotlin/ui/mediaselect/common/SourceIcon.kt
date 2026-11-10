package com.wynime.app.ui.mediaselect.common

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.wynime.app.ui.foundation.AsyncImage
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

@Composable
fun SourceIcon(
    iconUrl: String,
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    // Keep a stable, source-specific fallback for plugins that do not publish
    // an icon. The key is important because the selector reuses card slots;
    // without it Sketch can retain the previous source's image state.
    val fallbackIconUrl = remember(sourceName) {
        URLBuilder().apply {
            takeFrom("https://ui-avatars.com/api")
            parameters.append("name", sourceName)
        }.buildString()
    }
    var useFallback by remember(iconUrl, sourceName) { mutableStateOf(iconUrl.isBlank()) }
    val resolvedIconUrl = if (useFallback) fallbackIconUrl else iconUrl.trim()
    val iconModel = resolvedIconUrl.ifEmpty {
        // This branch is only needed for an empty source name in a malformed
        // plugin; keep a valid model so the image component never receives "".
        fallbackIconUrl
    }
    key(iconModel) {
        AsyncImage(
            iconModel,
            contentDescription = sourceName,
            Modifier.clip(CircleShape).then(modifier),
            contentScale = ContentScale.Crop,
            onError = { useFallback = true },
        )
    }
}
