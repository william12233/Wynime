package com.wynime.app.ui.foundation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropSourceModifierNode
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.panpf.sketch.LocalPlatformContext
import com.github.panpf.sketch.request.ImageResult
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.zoom.GestureType
import com.github.panpf.zoomimage.zoom.MouseWheelScaleCalculator
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.platform.PlatformWindow
import com.wynime.app.platform.window.rememberLayoutHitTestOwner
import com.wynime.app.ui.foundation.LocalSketch
import com.wynime.app.ui.foundation.imageviewer.FileKitImageFileSaver
import com.wynime.app.ui.foundation.imageviewer.ImageViewerContent
import com.wynime.app.ui.foundation.imageviewer.ImageViewerExportedFile
import com.wynime.app.ui.foundation.imageviewer.ImageViewerWindowBounds
import com.wynime.app.ui.foundation.imageviewer.computeImageViewerWindowBounds
import com.wynime.app.ui.foundation.imageviewer.imageViewerImageRequest
import com.wynime.app.ui.foundation.imageviewer.screenDensity
import com.wynime.app.ui.foundation.imageviewer.usableScreenArea
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.foundation.layout.LocalSecondaryWindowFrame
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.image_viewer_save
import com.wynime.app.ui.lang.image_viewer_window_title
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.readBytes
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.isWindows
import org.jetbrains.compose.resources.stringResource
import java.awt.MouseInfo
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val logger = logger("ImageViewer")

@Composable
actual fun ImageViewer(handler: ImageViewerHandler, onClose: () -> Unit) {
    val hostWindow = hostAwtWindow()
    if (hostWindow == null) {
        ImageViewerOverlay(handler, onClose)
        return
    }
    if (!handler.viewing.value) return
    ImageViewerWindow(handler, hostWindow, onClose)
}

@Composable
actual fun ImageViewerBackHandler(handler: ImageViewerHandler) {
    if (hostAwtWindow() == null) {
        ImageViewerOverlayBackHandler(handler)
    }
}

@Composable
private fun hostAwtWindow(): java.awt.Window? {
    return (LocalPlatformWindow.current.windowScope as? FrameWindowScope)?.window
}

@Composable
private fun ImageViewerWindow(
    handler: ImageViewerHandler,
    hostWindow: java.awt.Window,
    onClose: () -> Unit,
) {
    val model by handler.imageModel.collectAsStateWithLifecycle()
    val onCloseState = rememberUpdatedState(onClose)
    val screen = remember(hostWindow) { usableScreenArea(hostWindow) }
    val density = remember(hostWindow) { screenDensity(hostWindow) }

    val decodeSize = remember(screen, density) {
        IntSize(
            (screen.width.value * density * DECODE_SIZE_MULTIPLIER).roundToInt(),
            (screen.height.value * density * DECODE_SIZE_MULTIPLIER).roundToInt(),
        )
    }

    val sketch = LocalSketch.current
    val platformContext = LocalPlatformContext.current
    val initialModel = remember { model }
    var prefetchedBounds by remember { mutableStateOf<ImageViewerWindowBounds?>(null) }
    var prefetchTimedOut by remember { mutableStateOf(initialModel == null) }
    LaunchedEffect(sketch, platformContext, initialModel) {
        val m = initialModel ?: return@LaunchedEffect
        val result = async { sketch.execute(imageViewerImageRequest(platformContext, m, decodeSize)) }

        val timely = withTimeoutOrNull(PREFETCH_TIMEOUT) { result.await() }
        if (timely == null) prefetchTimedOut = true
        val success = result.await() as? ImageResult.Success ?: run {
            prefetchTimedOut = true
            return@LaunchedEffect
        }
        prefetchedBounds = computeImageViewerWindowBounds(
            IntSize(success.image.width, success.image.height), density, screen,
        )
    }
    val initialBounds = prefetchedBounds
    if (initialBounds == null && !prefetchTimedOut) return

    val windowState = rememberWindowState(
        size = initialBounds?.size ?: INITIAL_WINDOW_SIZE,
        position = initialBounds?.let { WindowPosition.Absolute(it.position.x, it.position.y) }
            ?: WindowPosition.Aligned(Alignment.Center),
    )

    val icon = remember(hostWindow) {
        (hostWindow.iconImages.firstOrNull() as? BufferedImage)?.toPainter()
    }
    Window(
        onCloseRequest = { onCloseState.value() },
        state = windowState,
        title = stringResource(Lang.image_viewer_window_title),
        icon = icon,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onCloseState.value()
                true
            } else {
                false
            }
        },
    ) {
        val platform = LocalPlatform.current

        val layoutHitTestOwner = if (platform.isWindows()) rememberLayoutHitTestOwner() else null
        val platformWindow = remember(window.windowHandle, this, platform, windowState, layoutHitTestOwner) {
            PlatformWindow(
                windowHandle = window.windowHandle,
                windowScope = this,
                windowState = windowState,
                platform = platform,
                layoutHitTestOwner = layoutHitTestOwner,
            )
        }
        val window = this.window
        val saveDialogTitle = stringResource(Lang.image_viewer_save)
        val content: @Composable () -> Unit = {
            ImageViewerContent(
                model = model,
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),

                closeOnTap = false,
                showCloseButton = false,
                fileSaver = remember(window, saveDialogTitle) {

                    FileKitImageFileSaver(FileKitDialogSettings(title = saveDialogTitle, parentWindow = window))
                },
                platformImageModifier = { exported, zoomable ->
                    Modifier
                        .imageDragOut(exported, zoomable)
                        .imageScrollPan(zoomable)
                },

                contentScale = ContentScale.Inside,
                decodeSize = decodeSize,

                onImageSizeAvailable = { imageSize ->
                    val bounds = computeImageViewerWindowBounds(imageSize, density, screen)
                    if (windowState.size != bounds.size) {
                        windowState.size = bounds.size
                        windowState.position = WindowPosition.Absolute(bounds.position.x, bounds.position.y)
                    }
                },
            )
        }
        CompositionLocalProvider(LocalPlatformWindow provides platformWindow) {
            val frame = LocalSecondaryWindowFrame.current
            if (frame != null) {
                frame(windowState, onClose, content)
            } else {
                content()
            }
        }
    }
}

private const val SCALE_EPSILON = 0.001f

private val PREFETCH_TIMEOUT = 800.milliseconds

private const val DECODE_SIZE_MULTIPLIER = 2f

private val INITIAL_WINDOW_SIZE = DpSize(800.dp, 600.dp)

private const val DRAG_DECORATION_MAX_SIZE = 240

@Composable
private fun Modifier.imageDragOut(exported: ImageViewerExportedFile?, zoomable: ZoomableState): Modifier {
    val atFitScale = zoomable.isAtFitScale()
    LaunchedEffect(zoomable, atFitScale) {

        zoomable.setDisabledGestureTypes(if (atFitScale) GestureType.ONE_FINGER_DRAG else 0)
    }
    return this.then(ImageDragOutElement(exported, zoomable))
}

private fun ZoomableState.isAtFitScale(): Boolean = transform.scaleX <= minScale + SCALE_EPSILON

private data class ImageDragOutElement(
    val exported: ImageViewerExportedFile?,
    val zoomable: ZoomableState,
) : ModifierNodeElement<ImageDragOutNode>() {
    override fun create() = ImageDragOutNode(exported, zoomable)

    override fun update(node: ImageDragOutNode) {
        node.update(exported, zoomable)
    }
}

private class ImageDragOutNode(
    private var exported: ImageViewerExportedFile?,
    private var zoomable: ZoomableState,
) : DelegatingNode() {
    private var decoration: ImageBitmap? = null

    private val source = delegate(
        DragAndDropSourceModifierNode { _ ->
            val file = exported ?: return@DragAndDropSourceModifierNode
            val awtFile = File(file.path.absolutePath)
            logger.info { "Dragging image out: ${awtFile.name}" }
            val thumbnail = decoration ?: runCatching { decodeImageBitmap(file.path.readBytes()) }.getOrNull()
                ?.also { decoration = it }
            val decorationSize = thumbnail?.let {
                val scale = DRAG_DECORATION_MAX_SIZE.toFloat() / maxOf(it.width, it.height, 1)
                Size(it.width * scale, it.height * scale)
            } ?: Size.Zero
            startDragAndDropTransfer(
                transferData = DragAndDropTransferData(
                    transferable = DragAndDropTransferable(FileListTransferable(listOf(awtFile))),
                    supportedActions = listOf(DragAndDropTransferAction.Copy),
                ),
                decorationSize = decorationSize,
                drawDragDecoration = {
                    if (thumbnail != null) {
                        drawImage(
                            thumbnail,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            alpha = 0.9f,
                        )
                    }
                },
            )
        },
    )

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (exported == null || !zoomable.isAtFitScale()) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > slop) {
                            change.consume()
                            source.requestDragAndDropTransfer(change.position)
                            break
                        }
                    }
                }
            },
        )
    }

    fun update(exported: ImageViewerExportedFile?, zoomable: ZoomableState) {
        if (this.exported !== exported) decoration = null
        this.exported = exported
        this.zoomable = zoomable
    }
}

private val SCROLL_PAN_STEP = 64.dp

@Composable
internal fun Modifier.imageScrollPan(zoomable: ZoomableState): Modifier {
    val calculator = remember { ModifierAwareWheelScaleCalculator() }
    LaunchedEffect(zoomable, calculator) {
        zoomable.setMouseWheelScaleCalculator(calculator)
    }
    return this.then(ImageScrollPanElement(zoomable, calculator))
}

private class ModifierAwareWheelScaleCalculator : MouseWheelScaleCalculator {
    @Volatile
    var zoomModifierPressed: Boolean = false

    override fun calculateScale(currentScale: Float, scrollDelta: Float): Float {
        return if (zoomModifierPressed) {
            MouseWheelScaleCalculator.Default.calculateScale(currentScale, scrollDelta)
        } else {
            currentScale
        }
    }
}

private data class ImageScrollPanElement(
    val zoomable: ZoomableState,
    val calculator: ModifierAwareWheelScaleCalculator,
) : ModifierNodeElement<ImageScrollPanNode>() {
    override fun create() = ImageScrollPanNode(zoomable, calculator)

    override fun update(node: ImageScrollPanNode) {
        node.zoomable = zoomable
        node.calculator = calculator
    }
}

private class ImageScrollPanNode(
    var zoomable: ZoomableState,
    var calculator: ModifierAwareWheelScaleCalculator,
) : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                val step = SCROLL_PAN_STEP.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val modifiers = event.keyboardModifiers
                        val zoomModifierPressed = modifiers.isCtrlPressed || modifiers.isMetaPressed
                        calculator.zoomModifierPressed = zoomModifierPressed
                        if (zoomModifierPressed) continue
                        val delta = change.scrollDelta
                        if (delta == Offset.Zero) continue
                        change.consume()
                        val zoomable = zoomable
                        coroutineScope.launch {
                            zoomable.offsetBy(Offset(-delta.x * step, -delta.y * step), animated = false)
                        }
                    }
                }
            },
        )
    }
}

private class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return files
    }
}
