package com.wynime.app.ui.settings.rendering

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalIsPreviewing
import com.wynime.datasources.api.source.MediaSourceInfo

@Composable
fun MediaSourceInfo.getIconResourceOrNull(): Painter? = null

@Composable
fun MediaSourceIcon(
    sourceInfo: MediaSourceInfo?,
    modifier: Modifier = Modifier,
) {
    val url = sourceInfo?.getIconResourceOrNull()
    when {
        url != null && !LocalIsPreviewing.current -> {
            Image(
                url,
                null,
                modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                colorFilter = null,
            )
        }

        sourceInfo != null -> {
            AsyncImage(
                sourceInfo.iconUrl?.takeIf { it.isNotEmpty() } ?: MediaSourceIcons.getDefaultIconUrl(sourceInfo),
                null,
                modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                colorFilter = null,
            )
        }

        else -> {
            Image(
                rememberVectorPainter(Icons.Rounded.DisplaySettings), null,
                modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
fun SmallMediaSourceIcon(
    info: MediaSourceInfo,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(MaterialTheme.shapes.extraSmall).height(24.dp)) {
        val image = info.getIconResourceOrNull()
        when {
            image != null -> {
                Image(
                    image,
                    null, Modifier.size(24.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    colorFilter = null,
                )
            }

            else -> {
                AsyncImage(
                    info.iconUrl?.takeIf { it.isNotEmpty() } ?: MediaSourceIcons.getDefaultIconUrl(info),
                    null, Modifier.size(24.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    colorFilter = null,
                )
            }
        }
    }
}
