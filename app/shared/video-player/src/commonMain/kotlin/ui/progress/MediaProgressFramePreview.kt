package com.wynime.app.videoplayer.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.FramePreview
import org.openani.mediamp.features.PreviewFrame

@Stable
class MediaProgressFramePreviewState(

    private val fetchFrame: suspend (positionMillis: Long) -> ImageBitmap?,
    private val debounceMillis: Long = 50,

    private val positionGridMillis: Long = 2_000,

    cacheSize: Int = 8,
) {

    var frame: ImageBitmap? by mutableStateOf(null)
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    private var frameGridKey = Long.MIN_VALUE
    private val cache = androidx.collection.LruCache<Long, ImageBitmap>(cacheSize)

    private fun gridKeyOf(positionMillis: Long): Long =
        if (positionGridMillis > 0) positionMillis / positionGridMillis else positionMillis

    suspend fun requestFrame(positionMillis: Long) {
        val key = gridKeyOf(positionMillis)
        if (key == frameGridKey && frame != null) return
        cache[key]?.let {
            frame = it
            frameGridKey = key
            return
        }
        isLoading = true
        try {
            delay(debounceMillis)
            val newFrame = fetchFrame(alignToGrid(key, positionMillis)) ?: return
            cache.put(key, newFrame)
            frame = newFrame
            frameGridKey = key
        } finally {
            isLoading = false
        }
    }

    suspend fun prewarm(positionMillis: Long) {
        val key = gridKeyOf(positionMillis)
        if (cache[key] != null) return
        val newFrame = fetchFrame(alignToGrid(key, positionMillis)) ?: return
        cache.put(key, newFrame)
    }

    private fun alignToGrid(key: Long, positionMillis: Long): Long =
        if (positionGridMillis > 0) key * positionGridMillis else positionMillis

    fun onPreviewFinished() {
        frame = null
        isLoading = false
        frameGridKey = Long.MIN_VALUE
    }

    fun onMediaChanged() {
        cache.evictAll()
        frame = null
        isLoading = false
        frameGridKey = Long.MIN_VALUE
    }
}

@Composable
fun rememberMediaProgressFramePreviewState(
    player: MediampPlayer,
    maxWidth: Dp = 192.dp,
    maxHeight: Dp = 128.dp,
): MediaProgressFramePreviewState? {
    val framePreview = remember(player) { player.features[FramePreview] } ?: return null
    val density = LocalDensity.current
    val state = remember(framePreview, density, maxWidth, maxHeight) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }
        MediaProgressFramePreviewState(
            fetchFrame = { positionMillis ->
                framePreview.getPreviewFrame(positionMillis, maxWidthPx, maxHeightPx)?.toImageBitmap()
            },
        )
    }
    LaunchedEffect(state, player) {
        player.mediaData.collect { data ->
            state.onMediaChanged()
            if (data != null) {

                runCatching { state.prewarm(player.currentPositionMillis.value) }
            }
        }
    }
    return state
}

fun createMediaProgressFramePreviewState(
    player: MediampPlayer,
    maxWidth: Int,
    maxHeight: Int,
): MediaProgressFramePreviewState? {
    val feature = player.features[FramePreview] ?: return null
    return MediaProgressFramePreviewState(fetchFrame = { position ->
        feature.getPreviewFrame(position, maxWidth, maxHeight)?.toImageBitmap()
    })
}

internal expect fun PreviewFrame.toImageBitmap(): ImageBitmap
