package com.wynime.app.videoplayer.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.Flow
import kotlin.enums.enumEntries

internal typealias PageTypeUpperBound<P> = Enum<P>

@Composable
inline fun <reified P : PageTypeUpperBound<P>> VideoSideSheets(
    controller: VideoSideSheetsController<P>,
    modifier: Modifier = Modifier,
    noinline pageContent: @Composable (VideoSideSheetScope.(page: P) -> Unit),
) {
    VideoSideSheets(controller, enumEntries(), modifier, pageContent)
}

@Composable
fun <P : PageTypeUpperBound<P>> VideoSideSheets(
    controller: VideoSideSheetsController<P>,
    pages: List<P>,
    modifier: Modifier = Modifier,
    pageContent: @Composable (VideoSideSheetScope.(page: P) -> Unit),
) {
    val backStack = controller.backStack
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { controller.goBack() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = { fadeIn(snap()) togetherWith fadeOut(snap()) },
        popTransitionSpec = { fadeIn(snap()) togetherWith fadeOut(snap()) },
        predictivePopTransitionSpec = { fadeIn(snap()) togetherWith fadeOut(snap()) },
        entryProvider = entryProvider {
            entry<VideoSideSheetRoute.None> {

            }
            entry<VideoSideSheetRoute.Page> { route ->
                pages.firstOrNull { it.name == route.name }?.let { page ->
                    val scope = remember(controller, route) {
                        VideoSideSheetScopeImpl(controller, route)
                    }
                    pageContent(scope, page)
                }
            }
        },
    )
}

internal sealed class VideoSideSheetRoute : NavKey {
    data object None : VideoSideSheetRoute()

    data class Page(val name: String, val index: Int) : VideoSideSheetRoute()
}

@Stable
sealed class VideoSideSheetsController<P : PageTypeUpperBound<P>> {
    internal abstract val backStack: SnapshotStateList<VideoSideSheetRoute>

    val hasPageFlow: Flow<Boolean>
        get() = snapshotFlow { backStack.lastOrNull() is VideoSideSheetRoute.Page }

    fun navigateTo(route: P) {
        backStack.add(VideoSideSheetRoute.Page(route.name, backStack.size))
    }

    internal fun goBack() {
        popTo(backStack.lastIndex)
    }

    internal fun closeSideSheet() {
        popTo(1)
    }

    private fun popTo(targetSize: Int) {
        val size = targetSize.coerceAtLeast(1)
        while (backStack.size > size) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

@Composable
fun <P : PageTypeUpperBound<P>> VideoSideSheetsController<P>.hasPageAsState(): State<Boolean> {
    return hasPageFlow.collectAsState(initial = false)
}

@Composable
fun <P : PageTypeUpperBound<P>> rememberVideoSideSheetsController(): VideoSideSheetsController<P> {
    val backStack = rememberSaveable(saver = VideoSideSheetBackStackSaver) {
        mutableStateListOf<VideoSideSheetRoute>(VideoSideSheetRoute.None)
    }
    return remember(backStack) {
        VideoSideSheetsControllerImpl(backStack)
    }
}

private val VideoSideSheetBackStackSaver: Saver<SnapshotStateList<VideoSideSheetRoute>, Any> = listSaver(
    save = { stack ->
        stack.map { route ->
            when (route) {
                VideoSideSheetRoute.None -> ""
                is VideoSideSheetRoute.Page -> "${route.index}:${route.name}"
            }
        }
    },
    restore = { saved ->

        if (saved.isEmpty()) {
            null
        } else {
            saved.map { entry ->
                val value = entry as String
                val separator = value.indexOf(':')
                if (separator == -1) {
                    VideoSideSheetRoute.None
                } else {
                    VideoSideSheetRoute.Page(
                        name = value.substring(separator + 1),
                        index = value.substring(0, separator).toInt(),
                    )
                }
            }.toMutableStateList()
        }
    },
)

@Stable
sealed interface VideoSideSheetScope {

    fun goBack()

    fun closeSideSheet()
}

private class VideoSideSheetsControllerImpl<P : PageTypeUpperBound<P>>(
    override val backStack: SnapshotStateList<VideoSideSheetRoute>,
) : VideoSideSheetsController<P>()

internal class VideoSideSheetScopeImpl(
    private val controller: VideoSideSheetsController<*>,
    private val route: VideoSideSheetRoute,
) : VideoSideSheetScope {
    override fun goBack() {

        val index = controller.backStack.indexOfLast { it == route }
        if (index <= 0) return
        while (controller.backStack.size > index) {
            controller.backStack.removeAt(controller.backStack.lastIndex)
        }
    }

    override fun closeSideSheet() {
        controller.closeSideSheet()
    }
}
