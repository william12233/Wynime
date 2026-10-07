package com.wynime.app.ui.foundation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.window_drop_unsupported_description
import com.wynime.app.ui.lang.window_drop_unsupported_title
import com.wynime.utils.io.name
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.roundToInt

@Stable
interface WindowDropHandler {

    fun onDragStarted(content: DragAndDropContent?): WindowDropPreview?

    fun onDrop(content: DragAndDropContent): Boolean

    @Composable
    fun supportedHint(): String? = null
}

enum class WindowDropTone {

    ACCEPT,

    REJECT,
}

@Stable
class WindowDropPreview(
    val tone: WindowDropTone = WindowDropTone.ACCEPT,
    val content: @Composable () -> Unit,
)

@Stable
sealed interface WindowDropSession {

    class Accepted(val handler: WindowDropHandler, val preview: WindowDropPreview) : WindowDropSession

    class Rejected(val fileName: String) : WindowDropSession
}

@Stable
class WindowDropHostState {
    var session: WindowDropSession? by mutableStateOf(null)
        private set

    private var acceptedByContent = false

    fun onDragStarted(content: DragAndDropContent?, handlers: List<WindowDropHandler>) {
        acceptedByContent = content != null
        if (handlers.isEmpty()) {
            session = null
            return
        }
        session = findHandler(content, handlers)
            ?: (content as? DragAndDropContent.FileList)?.files?.firstOrNull()
                ?.let { WindowDropSession.Rejected(it.name) }
    }

    fun onDrop(content: DragAndDropContent, handlers: List<WindowDropHandler>): Boolean {
        val accepted = (session as? WindowDropSession.Accepted)?.handler
        val handler = accepted?.takeIf { acceptedByContent }
            ?: findHandler(content, handlers)?.handler
            ?: accepted
            ?: return false
        return handler.onDrop(content)
    }

    fun onDragEnded() {
        session = null
        acceptedByContent = false
    }

    private fun findHandler(
        content: DragAndDropContent?,
        handlers: List<WindowDropHandler>,
    ): WindowDropSession.Accepted? {
        for (handler in handlers) {
            val preview = handler.onDragStarted(content) ?: continue
            return WindowDropSession.Accepted(handler, preview)
        }
        return null
    }
}

object WindowDropTestTags {
    const val OVERLAY = "window_drop_overlay"
}

@Stable
class WindowDropHandlerRegistry {
    private val _handlers = mutableStateListOf<WindowDropHandler>()

    val handlers: List<WindowDropHandler> get() = _handlers

    fun register(handler: WindowDropHandler) {
        _handlers.add(0, handler)
    }

    fun unregister(handler: WindowDropHandler) {
        _handlers.remove(handler)
    }
}

val LocalWindowDropHandlerRegistry = compositionLocalOf<WindowDropHandlerRegistry?> { null }

@Composable
fun WindowDropHandlerEffect(handler: WindowDropHandler) {
    val registry = LocalWindowDropHandlerRegistry.current ?: return
    DisposableEffect(registry, handler) {
        registry.register(handler)
        onDispose { registry.unregister(handler) }
    }
}

@Composable
fun WindowDropHost(
    handlers: List<WindowDropHandler>,
    modifier: Modifier = Modifier,
    state: WindowDropHostState = remember { WindowDropHostState() },
    content: @Composable () -> Unit,
) {
    val registry = remember { WindowDropHandlerRegistry() }
    val currentHandlers by rememberUpdatedState(registry.handlers + handlers)
    val target = remember(state) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {

                val content = runCatching { processDragAndDropEventImpl(event) }.getOrNull()
                state.onDragStarted(content, currentHandlers)
            }

            override fun onEnded(event: DragAndDropEvent) {
                state.onDragEnded()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val content = runCatching { processDragAndDropEventImpl(event) }
                    .getOrDefault(DragAndDropContent.Unsupported)
                return state.onDrop(content, currentHandlers)
            }
        }
    }

    Box(modifier.dragAndDropTarget({ currentHandlers.isNotEmpty() }, target)) {
        CompositionLocalProvider(LocalWindowDropHandlerRegistry provides registry) {
            content()
        }

        val session = state.session

        var lastSession by remember { mutableStateOf(session) }
        if (session != null) lastSession = session
        AnimatedVisibility(
            visible = session != null,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 150)),
        ) {
            lastSession?.let { WindowDropOverlay(it, currentHandlers) }
        }
    }
}

@Composable
private fun WindowDropOverlay(session: WindowDropSession, handlers: List<WindowDropHandler>) {
    when (session) {
        is WindowDropSession.Accepted -> WindowDropOverlayLayer(session.preview.tone) {
            session.preview.content()
        }

        is WindowDropSession.Rejected -> WindowDropOverlayLayer(WindowDropTone.REJECT) {
            val hints = handlers.mapNotNull { it.supportedHint() }
            WindowDropCardContent(
                icon = Icons.Rounded.Block,
                title = stringResource(Lang.window_drop_unsupported_title),
                subtitle = session.fileName,
                description = if (hints.isEmpty()) null else stringResource(
                    Lang.window_drop_unsupported_description,
                    hints.joinToString("、"),
                ),
                tone = WindowDropTone.REJECT,
            )
        }
    }
}

private val DropCardCornerRadius = 28.dp

@Composable
private fun WindowDropOverlayLayer(
    tone: WindowDropTone,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val scrimColor = if (isDark) colors.scrim.copy(alpha = 0.72f) else colors.surface.copy(alpha = 0.8f)
    Box(
        modifier
            .fillMaxSize()
            .background(scrimColor)
            .testTag(WindowDropTestTags.OVERLAY),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .width(560.dp)
                .dashedBorder(if (tone == WindowDropTone.REJECT) colors.error else colors.primary, DropCardCornerRadius),
            shape = RoundedCornerShape(DropCardCornerRadius),
            color = colors.surfaceContainer,
            shadowElevation = 6.dp,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(start = 40.dp, top = 40.dp, end = 40.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
fun WindowDropCardContent(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    badge: String? = null,
    tone: WindowDropTone = WindowDropTone.ACCEPT,
) {
    val colors = MaterialTheme.colorScheme
    val reject = tone == WindowDropTone.REJECT
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(
                    if (reject) colors.errorContainer else colors.primaryContainer,
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(36.dp),
                tint = if (reject) colors.onErrorContainer else colors.onPrimaryContainer,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        if (subtitle != null) {
            Surface(
                shape = CircleShape,
                color = if (reject) colors.errorContainer else colors.secondaryContainer,
                contentColor = if (reject) colors.onErrorContainer else colors.onSecondaryContainer,
            ) {
                Text(
                    subtitle,
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (badge != null) {
            Surface(
                Modifier.padding(top = 4.dp),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = colors.outline,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Text(
                    badge,
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    width: Dp = 2.dp,
    dash: Dp = 14.dp,
): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2
    val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    val perimeter = 2 * (rectSize.width + rectSize.height) - 8 * radius + 2 * PI.toFloat() * radius
    val segments = (perimeter / (2 * dash.toPx())).roundToInt().coerceAtLeast(1)
    val segment = perimeter / (2 * segments)
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = rectSize,
        cornerRadius = CornerRadius(radius),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,

            pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment - strokeWidth, segment + strokeWidth)),
        ),
    )
}
