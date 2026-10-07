package com.wynime.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerType
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.ActiveInputSourceState
import com.wynime.app.ui.foundation.input.LocalActiveInputSource

@Composable
@ExperimentalMaterial3Api
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    enabled: Boolean = true,
    touchOnly: Boolean = false,
    indicator: @Composable BoxScope.() -> Unit = {
        Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            isRefreshing = isRefreshing,
            state = state,
        )
    },
    content: @Composable BoxScope.() -> Unit
) {
    val activeInputSource = LocalActiveInputSource.current
    val touchOnlyEnabled = rememberUpdatedState(enabled && touchOnly)
    val touchOnlyConnection = remember(activeInputSource) {
        TouchOnlyPullToRefreshNestedScrollConnection(
            activeInputSource = activeInputSource,
            enabled = { touchOnlyEnabled.value },
        )
    }

    Box(
        modifier
            .pullToRefresh(state = state, isRefreshing = isRefreshing, enabled = enabled, onRefresh = onRefresh)
            .ifThen(touchOnly) { nestedScroll(touchOnlyConnection) },
        contentAlignment = contentAlignment,
    ) {
        content()
        indicator()
    }
}

private class TouchOnlyPullToRefreshNestedScrollConnection(
    private val activeInputSource: ActiveInputSourceState,
    private val enabled: () -> Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (
            enabled() &&
            source == NestedScrollSource.UserInput &&
            activeInputSource.latest != PointerType.Touch &&
            available.y > 0f
        ) {
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }
}
