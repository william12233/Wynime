package com.wynime.app.ui.mediaselect.common

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.wynime.app.ui.foundation.AsyncImage

@Composable
fun SourceIcon(
    iconUrl: String,
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        iconUrl,
        contentDescription = sourceName,
        Modifier.clip(CircleShape).then(modifier),
        contentScale = ContentScale.Crop,
    )
}
