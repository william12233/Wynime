package com.wynime.app.ui.foundation.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wynime.app.tools.MonoTasker
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview

@Stable
inline val ProgressIndicatorHeight get() = 4.dp

enum class Mode {
    Indefinite,
    Definite,
}

private val ScaleAnimation: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium)

@Stable
class FastLinearProgressState(
    uiScope: CoroutineScope,
) {
    private val tasker = MonoTasker(uiScope)
    private var targetVisible by mutableStateOf(false)

    internal var mode: Mode by mutableStateOf(Mode.Indefinite)
    internal var scale: Float by mutableFloatStateOf(0f)
        private set
    internal var progress: Float by mutableFloatStateOf(0f)
        private set

    fun setVisible(
        visible: Boolean,
        delayMillis: Long = 100L,
        minimumDurationMillis: Int = 1500,
    ) {
        if (targetVisible == visible) return
        targetVisible = visible

        if (visible) {
            this.mode = Mode.Definite
            tasker.launch {
                delay(delayMillis)
                launch {
                    animate(
                        scale, 1f,
                        animationSpec = ScaleAnimation,
                    ) { value, _ ->
                        scale = value
                    }
                }
                animate(
                    0f, 1f,
                    animationSpec = tween(minimumDurationMillis),
                ) { value, _ ->
                    progress = value
                }

                if (targetVisible) {
                    mode = Mode.Indefinite
                } else {

                }
            }
        } else {
            tasker.launchNext {
                animate(
                    scale, 0f,
                    animationSpec = ScaleAnimation,
                ) { value, _ ->
                    scale = value
                }
                mode = Mode.Definite
            }
        }
    }

    suspend fun awaitCompletion() {
        withContext(Dispatchers.Main.immediate) {
            if (!targetVisible) return@withContext
            snapshotFlow { targetVisible }.filter { !it }.first()
            tasker.join()
        }
    }
}

@Composable
fun FastLinearProgressIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
    delayMillis: Long = 100L,
    minimumDurationMillis: Int = 1500,
) {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { FastLinearProgressState(scope) }
    SideEffect {
        state.setVisible(visible, delayMillis, minimumDurationMillis)
    }
    return FastLinearProgressIndicator(state, modifier)
}

@Composable
fun FastLinearProgressIndicator(
    state: FastLinearProgressState,
    modifier: Modifier = Modifier,
) {
    val progressModifier = Modifier.fillMaxWidth().graphicsLayer { scaleY = state.scale }
    val trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val cap = StrokeCap.Round

    Crossfade(state.mode, modifier = modifier.height(4.dp).fillMaxWidth()) { mode ->
        if (mode == Mode.Definite) {
            LinearProgressIndicator(
                { state.progress },
                progressModifier,
                trackColor = trackColor,
                strokeCap = cap,
            )
        } else {
            LinearProgressIndicator(
                progressModifier,
                trackColor = trackColor,
                strokeCap = cap,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun PreviewAnimatedLinearProgressIndicatorIndefiniteLonger() = ProvideCompositionLocalsForPreview {
    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = true) {
        while (isActive) {
            visible = !visible
            delay(2000)
        }
    }
    Surface {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FastLinearProgressIndicator(visible)
        }
    }
}

@PreviewLightDark
@Composable
fun PreviewAnimatedLinearProgressIndicatorIndefiniteShorter() = ProvideCompositionLocalsForPreview {
    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = true) {
        while (isActive) {
            visible = !visible
            delay(1000)
        }
    }
    Surface {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FastLinearProgressIndicator(visible)
        }
    }
}
