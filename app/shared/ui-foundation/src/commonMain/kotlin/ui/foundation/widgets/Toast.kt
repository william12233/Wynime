package com.wynime.app.ui.foundation.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.tools.MonoTasker
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_load_error_network
import com.wynime.app.ui.lang.foundation_load_error_no_results
import com.wynime.app.ui.lang.foundation_load_error_rate_limited
import com.wynime.app.ui.lang.foundation_load_error_request_error
import com.wynime.app.ui.lang.foundation_load_error_requires_login
import com.wynime.app.ui.lang.foundation_load_error_service_unavailable
import com.wynime.app.ui.lang.foundation_load_error_unknown_feedback
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.math.max
import kotlin.math.min

@Stable
val LocalToaster: ProvidableCompositionLocal<Toaster> = staticCompositionLocalOf {
    error("No LocalToaster provided")
}

@Stable
interface Toaster {
    fun toast(text: String)

    fun show(text: String) {
        toast(text)
    }
}

private val logger = logger<Toaster>()

@OptIn(DelicateCoroutinesApi::class)
fun Toaster.showLoadError(error: LoadError) {
    GlobalScope.launch(Dispatchers.Main) {
        show(renderLoadErrorToastMessage(error))
    }

    when (error) {
        is LoadError.UnknownError -> {
            logger.warn(error.throwable) {
                "Toaster showing an LoadError.UnknownError"
            }
        }

        is LoadError.RequestError -> {
            logger.warn {
                "Toaster showing an LoadError.RequestError: ${error.localized}"
            }
        }

        else -> {}
    }
}

private suspend fun renderLoadErrorToastMessage(error: LoadError): String {
    return when (error) {
        LoadError.NetworkError -> getString(Lang.foundation_load_error_network)
        LoadError.NoResults -> getString(Lang.foundation_load_error_no_results)
        LoadError.RateLimited -> getString(Lang.foundation_load_error_rate_limited)
        LoadError.RequiresLogin -> getString(Lang.foundation_load_error_requires_login)
        LoadError.ServiceUnavailable -> getString(Lang.foundation_load_error_service_unavailable)
        is LoadError.UnknownError -> getString(Lang.foundation_load_error_unknown_feedback)
        is LoadError.RequestError -> getString(Lang.foundation_load_error_request_error, error.localized)
    }
}

@Stable
@TestOnly
object NoOpToaster : Toaster {
    override fun toast(text: String) {
    }
}

class ToastViewModel : AbstractViewModel() {
    private val tasker = MonoTasker(backgroundScope)

    val showing = MutableStateFlow(false)
    val content = MutableStateFlow("")

    fun show(text: String) {
        showing.update { true }
        content.update { text }

        tasker.launch {
            delay(4000L)
            showing.emit(false)
        }
    }
}

@Composable
fun Toast(
    showing: () -> Boolean,
    content: @Composable () -> Unit
) = BoxWithConstraints(Modifier.fillMaxSize()) box@{
    val px640dp = with(LocalDensity.current) { 640.dp.roundToPx() }
    val px100dp = with(LocalDensity.current) { 100.dp.roundToPx() }

    val minToastWidth = with(LocalDensity.current) { px100dp + 60.dp.roundToPx() * 2 }
    val maxToastWidth = max(minToastWidth, min(constraints.maxWidth, px640dp))

    val currentContent by rememberUpdatedState(content)

    WynimeAnimatedVisibility(
        visible = showing(),
        enter = fadeIn(tween(350, easing = LinearEasing)),
        exit = fadeOut(tween(350, easing = LinearEasing)),
        modifier = Modifier.layout { measurable, constraints ->
            val rawWidth = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE)).width

            val placeable = measurable.measure(
                constraints.copy(minWidth = min(rawWidth, minToastWidth), maxWidth = maxToastWidth, minHeight = 0),
            )

            val x = max(this@box.constraints.maxWidth - placeable.width, 0) / 2
            val y = constraints.maxHeight - placeable.height - px100dp

            layout(placeable.width, placeable.height) {
                placeable.place(x, y, 100f)
            }
        },
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 60.dp),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    currentContent()
                }
            }
        }
    }
}
