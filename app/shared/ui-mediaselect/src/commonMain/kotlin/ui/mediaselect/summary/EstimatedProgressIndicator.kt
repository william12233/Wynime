package com.wynime.app.ui.mediaselect.summary

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.wynime.utils.coroutines.SingleTaskExecutor

@Composable
fun rememberEstimatedProgressIndicatorState(
    initialProgress: Float = 0f,
    initialVisible: Boolean = false,
) = rememberSaveable(saver = EstimatedProgressIndicatorState.Saver) {
    EstimatedProgressIndicatorState(initialProgress, initialVisible)
}

@Stable
class EstimatedProgressIndicatorState private constructor(
    initialProgress: Float = 0f,
    initialHeightScale: Float = 0f,
) {
    constructor(
        initialProgress: Float = 0f,
        initialVisible: Boolean = false,
    ) : this(initialProgress, initialHeightScale = if (initialVisible) 1f else 0f)

    var progress by mutableFloatStateOf(initialProgress)
        private set
    var heightScale by mutableFloatStateOf(initialHeightScale)
        private set

    private var velocity by mutableFloatStateOf(0f)
    private val singleTaskExecutor = SingleTaskExecutor()

    suspend fun animateWithoutFinish(
        durationMillis: Int,
    ) {
        coroutineScope {
            if (heightScale < 1f) {
                launch {

                    animate(
                        initialValue = heightScale,
                        targetValue = 1f,
                        animationSpec = spring(),
                    ) { value, _ ->
                        this@EstimatedProgressIndicatorState.heightScale = value
                    }
                }
            }

            animate(
                initialValue = progress,
                targetValue = 0.99f,
                initialVelocity = velocity,
                animationSpec = tween(durationMillis, easing = LinearEasing),
            ) { p, v ->
                progress = p
                velocity = v
            }
        }
    }

    suspend fun finish() {
        if (progress >= 1f) {
            return
        }

        animate(
            initialValue = this.heightScale,
            targetValue = 0f,
            animationSpec = spring(),
        ) { value, _ ->
            this.heightScale = value
        }

        this.progress = 1f
    }

    companion object {
        val Saver = Saver<EstimatedProgressIndicatorState, Any>(
            save = { listOf(it.heightScale, it.progress) },
            restore = {
                it as List<*>
                EstimatedProgressIndicatorState(
                    initialProgress = it[1] as Float,
                    initialHeightScale = it[0] as Float,
                )
            },
        )
    }
}

@Composable
fun EstimatedLinearProgressIndictorBox(
    state: EstimatedProgressIndicatorState,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.height(4.dp),
        contentAlignment,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleY = state.heightScale
            },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}