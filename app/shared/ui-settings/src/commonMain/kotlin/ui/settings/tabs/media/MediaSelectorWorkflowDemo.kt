package com.wynime.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthCompact
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.utils.selectorworkflow.HighlightRegion
import com.wynime.utils.selectorworkflow.SelectorWorkflowAnimation
import com.wynime.utils.selectorworkflow.SelectorWorkflowViewModel
import kotlin.time.Duration

@Stable
class MediaSelectorWorkflowDemoState(
    initialEagerSelect: Boolean,
) {
    val viewModel = SelectorWorkflowViewModel()

    private var eagerSelect by mutableStateOf(initialEagerSelect)

    init {
        viewModel.configure(eager = initialEagerSelect)
    }

    fun onFastSelectWebKindChanged(enabled: Boolean) {
        eagerSelect = enabled

        viewModel.configure(eager = enabled, highlight = HighlightRegion.Results.takeIf { enabled })
    }

    fun syncEagerSelect(enabled: Boolean) {
        if (eagerSelect == enabled) return
        eagerSelect = enabled
        viewModel.configure(
            eager = enabled,
            priorityWaitSeconds = viewModel.config.selection.priorityWait?.inWholeSeconds?.toInt(),
            resolveBudgetSeconds = viewModel.config.resolve.budget.inWholeSeconds.toInt()
                .takeIf { viewModel.config.showInterceptClock },
            cacheQuery = viewModel.config.cachedQuery,
            highlight = viewModel.config.highlights.singleOrNull(),
            restart = false,
        )
    }

    fun onLowTierToleranceChanged(duration: Duration) {
        val seconds = duration.takeIf { it.isFinite() }?.inWholeSeconds?.toInt()?.takeIf { it > 0 }
        viewModel.configure(
            eager = eagerSelect,
            priorityWaitSeconds = seconds,
            highlight = HighlightRegion.Results.takeIf { seconds != null },
        )
    }

    fun onResolveTimeoutChanged(seconds: Int) {
        viewModel.configure(
            eager = eagerSelect,
            resolveBudgetSeconds = seconds.coerceAtLeast(1),
            highlight = HighlightRegion.Resolve,
        )
    }

    fun onWebSearchCacheTtlChanged(ttl: Duration) {
        val cached = ttl > Duration.ZERO
        viewModel.configure(
            eager = eagerSelect,
            cacheQuery = cached,
            highlight = HighlightRegion.Sources.takeIf { cached },
        )
    }
}

@Composable
fun rememberMediaSelectorWorkflowDemoState(eagerSelect: Boolean): MediaSelectorWorkflowDemoState {
    val state = remember { MediaSelectorWorkflowDemoState(eagerSelect) }

    LaunchedEffect(eagerSelect) { state.syncEagerSelect(eagerSelect) }
    return state
}

@Composable
fun SettingsScope.MediaSelectorWorkflowItem(
    state: MediaSelectorWorkflowDemoState,
    modifier: Modifier = Modifier,
) {
    val isWidthCompact = currentWindowAdaptiveInfo1().isWidthCompact
    Box(Modifier.fillMaxWidth()) {
        MediaSelectorWorkflowPreview(
            state,
            modifier
                .ifThen(isWidthCompact) { fillMaxWidth() }
                .ifThen(!isWidthCompact) { widthIn(max = 450.dp) }
                .padding(horizontal = SettingsScope.itemHorizontalPadding, vertical = 4.dp)
                .align(Alignment.Center),
        )
    }
}

@Composable
fun MediaSelectorWorkflowPreview(state: MediaSelectorWorkflowDemoState, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        SelectorWorkflowAnimation(state.viewModel, Modifier.padding(8.dp))
    }
}
