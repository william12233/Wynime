package com.wynime.app.ui.foundation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.imageviewer.ImageViewerContent
import com.wynime.app.ui.foundation.navigation.BackHandler

interface ImageViewerHandler {
    val imageModel: StateFlow<String?>
    val viewing: State<Boolean>

    fun viewImage(model: String?)
    fun clear()
}

val LocalImageViewerHandler: ProvidableCompositionLocal<ImageViewerHandler> = compositionLocalOf {
    error("no ImageViewerHandler provided")
}

const val IMAGE_VIEWER_TEST_TAG = "ImageViewer"

@Composable
fun rememberImageViewerHandler(): ImageViewerHandler {
    return remember {
        object : ImageViewerHandler {
            override val imageModel: MutableStateFlow<String?> = MutableStateFlow(null)
            override val viewing: MutableState<Boolean> = mutableStateOf(false)

            override fun viewImage(model: String?) {
                imageModel.value = model
                viewing.value = model != null
            }

            override fun clear() {
                imageModel.value = null
                viewing.value = false
            }
        }
    }
}

@Composable
expect fun ImageViewer(handler: ImageViewerHandler, onClose: () -> Unit)

@Composable
expect fun ImageViewerBackHandler(handler: ImageViewerHandler)

@Composable
internal fun ImageViewerOverlay(
    handler: ImageViewerHandler,
    onClose: () -> Unit,
) {
    val model by handler.imageModel.collectAsStateWithLifecycle()
    WynimeAnimatedVisibility(
        visible = handler.viewing.value,
        enter = LocalWynimeMotionScheme.current.animatedVisibility.standardEnter,
        exit = LocalWynimeMotionScheme.current.animatedVisibility.standardExit,
        modifier = Modifier.fillMaxSize(),
    ) {
        ImageViewerContent(
            model = model,
            onClose = onClose,
            modifier = Modifier.fillMaxSize(),
            closeOnTap = true,
            showCloseButton = true,
        )
    }
}

@Composable
internal fun ImageViewerOverlayBackHandler(handler: ImageViewerHandler) {
    BackHandler(enabled = handler.viewing.value) { handler.clear() }
}
