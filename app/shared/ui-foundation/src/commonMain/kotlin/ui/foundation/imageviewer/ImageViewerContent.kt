package com.wynime.app.ui.foundation.imageviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.LocalPlatformContext
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.LoadState
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.zoomimage.SketchZoomAsyncImage
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.rememberSketchZoomState
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.launch
import com.wynime.app.platform.ContextMP
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.files
import com.wynime.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import com.wynime.app.ui.foundation.LocalSketch
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.image_viewer_close
import com.wynime.app.ui.lang.image_viewer_copied
import com.wynime.app.ui.lang.image_viewer_copy
import com.wynime.app.ui.lang.image_viewer_copy_failed
import com.wynime.app.ui.lang.image_viewer_load_failed
import com.wynime.app.ui.lang.image_viewer_reset_zoom
import com.wynime.app.ui.lang.image_viewer_save
import com.wynime.app.ui.lang.image_viewer_save_failed
import com.wynime.app.ui.lang.image_viewer_saved
import com.wynime.app.ui.lang.image_viewer_zoom_in
import com.wynime.app.ui.lang.image_viewer_zoom_out
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.resolve
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

object ImageViewerTestTags {
    const val ZOOM_IN = "ImageViewer.ZoomIn"
    const val ZOOM_OUT = "ImageViewer.ZoomOut"
    const val RESET_ZOOM = "ImageViewer.ResetZoom"
    const val SCALE_TEXT = "ImageViewer.ScaleText"
    const val COPY = "ImageViewer.Copy"
    const val SAVE = "ImageViewer.Save"
    const val CLOSE = "ImageViewer.Close"
}

private const val ZOOM_STEP = 1.5f

private val logger = logger<ImageViewerTestTags>()

@Composable
fun ImageViewerContent(
    model: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeOnTap: Boolean = true,
    showCloseButton: Boolean = true,
    fileSaver: ImageFileSaver = rememberFileKitImageFileSaver(),
    imageClipboard: ImageClipboard? = rememberImageClipboard(),
    platformImageModifier: @Composable (exported: ImageViewerExportedFile?, zoomable: ZoomableState) -> Modifier =
        { _, _ -> Modifier },
    exportDirectory: SystemPath = imageViewerExportDirectory(LocalContext.current),
    contentScale: ContentScale = ContentScale.Fit,
    decodeSize: IntSize? = null,
    onImageSizeAvailable: (IntSize) -> Unit = {},
) {
    val sketch = LocalSketch.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val zoomState = rememberSketchZoomState()
    val imageState = rememberAsyncImageState()
    val loadState = imageState.loadState
    val loaded = loadState is LoadState.Success

    val onImageSizeAvailableState = rememberUpdatedState(onImageSizeAvailable)
    LaunchedEffect(loadState) {
        val image = (loadState as? LoadState.Success)?.result?.image ?: return@LaunchedEffect
        onImageSizeAvailableState.value(IntSize(image.width, image.height))
    }

    var exported by remember(model) { mutableStateOf<ImageViewerExportedFile?>(null) }
    LaunchedEffect(model, loaded) {
        if (model == null || !loaded) return@LaunchedEffect
        exported = try {
            sketch.exportImageForViewer(model, exportDirectory)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to export image for viewer: $model" }
            null
        }
    }

    val savedText = stringResource(Lang.image_viewer_saved)
    val saveFailedText = stringResource(Lang.image_viewer_save_failed)
    val copiedText = stringResource(Lang.image_viewer_copied)
    val copyFailedText = stringResource(Lang.image_viewer_copy_failed)

    val onCopy: (() -> Unit)? = if (imageClipboard == null) null else {
        {
            val file = exported
            if (file != null) {
                scope.launch {
                    try {
                        imageClipboard.copy(file)
                        toaster.toast(copiedText)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to copy image ${file.fileName}" }
                        toaster.toast(copyFailedText)
                    }
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(model, loaded) {
        if (loaded) runCatching { focusRequester.requestFocus() }
    }

    val imageModifier = platformImageModifier(exported, zoomState.zoomable)

    val tapGuard = remember { ImageViewerTapGuard() }
    Box(
        modifier
            .background(Color.Black)
            .onKeyEvent { event ->
                val isCopy = event.type == KeyEventType.KeyDown && event.key == Key.C &&
                        (event.isCtrlPressed || event.isMetaPressed)
                if (isCopy && onCopy != null) {
                    onCopy()
                    true
                } else {
                    false
                }
            },
    ) {
        if (model != null) {
            val platformContext = LocalPlatformContext.current
            val request = remember(platformContext, model, decodeSize) {
                imageViewerImageRequest(platformContext, model, decodeSize)
            }
            SketchZoomAsyncImage(
                request = request,
                contentDescription = null,
                sketch = sketch,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(IMAGE_VIEWER_TEST_TAG)
                    .focusRequester(focusRequester)
                    .focusable()
                    .pointerInput(tapGuard) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var dragged = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { (it.position - down.position).getDistance() > slop }) {
                                    dragged = true
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            tapGuard.dragged = dragged
                        }
                    }
                    .then(imageModifier),
                state = imageState,
                contentScale = contentScale,
                zoomState = zoomState,
                onTap = if (closeOnTap) {
                    { if (!tapGuard.dragged) onClose() }
                } else {
                    null
                },
            )
        }
        if (loadState is LoadState.Error) {
            Text(
                stringResource(Lang.image_viewer_load_failed),
                Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        ImageViewerToolbar(
            zoomable = zoomState.zoomable,
            scalePercent = if (loaded) zoomState.currentScalePercent() else null,
            enabled = loaded,
            canSave = exported != null,
            onCopy = onCopy,
            onSave = {
                val file = exported ?: return@ImageViewerToolbar
                scope.launch {
                    try {
                        if (fileSaver.save(file)) toaster.toast(savedText)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to save image ${file.fileName}" }
                        toaster.toast(saveFailedText)
                    }
                }
            },
            showCloseButton = showCloseButton,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )
    }
}

@Composable
private fun com.github.panpf.zoomimage.compose.ZoomState.currentScalePercent(): Int {
    val contentWidth = zoomable.contentSize.width
    val originalWidth = subsampling.imageInfo?.width
    val sourceFactor = if (originalWidth != null && originalWidth > 0 && contentWidth > 0) {
        contentWidth.toFloat() / originalWidth
    } else {
        1f
    }
    return (zoomable.transform.scaleX * sourceFactor * 100).roundToInt()
}

@Composable
private fun ImageViewerToolbar(
    zoomable: ZoomableState,
    scalePercent: Int?,
    enabled: Boolean,
    canSave: Boolean,
    onCopy: (() -> Unit)?,
    onSave: () -> Unit,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scale = zoomable.transform.scaleX
    val canZoomOut = enabled && scale > zoomable.minScale + SCALE_EPSILON
    val canZoomIn = enabled && scale < zoomable.maxScale - SCALE_EPSILON

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { scope.launch { zoomable.scale(scale / ZOOM_STEP, animated = true) } },
                enabled = canZoomOut,
                modifier = Modifier.testTag(ImageViewerTestTags.ZOOM_OUT),
            ) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = stringResource(Lang.image_viewer_zoom_out))
            }
            Text(
                text = scalePercent?.let { "$it%" } ?: "—",
                modifier = Modifier.testTag(ImageViewerTestTags.SCALE_TEXT),
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(
                onClick = { scope.launch { zoomable.scale(scale * ZOOM_STEP, animated = true) } },
                enabled = canZoomIn,
                modifier = Modifier.testTag(ImageViewerTestTags.ZOOM_IN),
            ) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = stringResource(Lang.image_viewer_zoom_in))
            }
            IconButton(
                onClick = { scope.launch { zoomable.scale(zoomable.minScale, animated = true) } },
                enabled = canZoomOut,
                modifier = Modifier.testTag(ImageViewerTestTags.RESET_ZOOM),
            ) {
                Icon(Icons.Rounded.FitScreen, contentDescription = stringResource(Lang.image_viewer_reset_zoom))
            }
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    enabled = canSave,
                    modifier = Modifier.testTag(ImageViewerTestTags.COPY),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(Lang.image_viewer_copy))
                }
            }
            IconButton(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.testTag(ImageViewerTestTags.SAVE),
            ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = stringResource(Lang.image_viewer_save))
            }
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag(ImageViewerTestTags.CLOSE),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(Lang.image_viewer_close))
                }
            }
        }
    }
}

private const val SCALE_EPSILON = 0.001f

private val exportDirectoryCleared = atomic(false)

fun imageViewerExportDirectory(context: ContextMP): SystemPath {
    val directory = context.files.cacheDir.resolve("image-viewer")
    if (exportDirectoryCleared.compareAndSet(expect = false, update = true)) {
        runCatching { directory.deleteRecursively() }
    }
    return directory
}

private class ImageViewerTapGuard {

    var dragged: Boolean = false
}

fun imageViewerImageRequest(context: PlatformContext, model: String, decodeSize: IntSize?): ImageRequest {
    return ImageRequest(context, model) {
        if (decodeSize != null) {

            size(decodeSize.width, decodeSize.height)
            precision(Precision.SMALLER_SIZE)
        }
    }
}
