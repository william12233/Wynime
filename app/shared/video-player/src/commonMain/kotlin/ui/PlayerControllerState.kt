package com.wynime.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.wynime.app.ui.foundation.effects.ComposeKey
import com.wynime.app.ui.foundation.effects.onKey
import com.wynime.app.ui.foundation.interaction.hoverable
import com.wynime.utils.platform.annotations.TestOnly

@Composable
fun rememberVideoControllerState(
    initialVisibility: ControllerVisibility = PlayerControllerState.DEFAULT_INITIAL_VISIBILITY
): PlayerControllerState {
    return remember {
        PlayerControllerState(initialVisibility)
    }
}

enum class PlayerFocusTarget {
    PLAYER,
    TEXT_INPUT,
}

@Stable
class PlayerFocusState {
    var preferredTarget: PlayerFocusTarget by mutableStateOf(PlayerFocusTarget.PLAYER)
        private set

    internal val requester = FocusRequester()
    private var textInputOwner: Any? = null

    fun preferPlayer() {
        textInputOwner = null
        preferredTarget = PlayerFocusTarget.PLAYER
    }

    internal fun preferTextInput(owner: Any) {
        textInputOwner = owner
        preferredTarget = PlayerFocusTarget.TEXT_INPUT
    }

    internal fun releaseTextInput(owner: Any) {
        if (textInputOwner === owner) {
            preferPlayer()
        }
    }

    internal fun requestPlayerFocus() {
        preferPlayer()
        requester.requestFocus()
    }

    internal fun restorePlayerFocusIfPreferred() {
        if (preferredTarget == PlayerFocusTarget.PLAYER) {
            requester.requestFocus()
        }
    }
}

@Composable
internal fun Modifier.playerFocusHost(
    state: PlayerFocusState,
    reapplyKey: Any?,
): Modifier {
    val preferredTarget = state.preferredTarget

    LaunchedEffect(state, preferredTarget, reapplyKey) {
        state.restorePlayerFocusIfPreferred()
    }
    return focusRequester(state.requester)
}

fun Modifier.playerTextInputFocus(
    state: PlayerFocusState,
    onEscape: (() -> Unit)? = null,
): Modifier = composed {
    val owner = remember { Any() }
    DisposableEffect(state, owner) {
        onDispose {
            state.releaseTextInput(owner)
        }
    }

    onFocusChanged {
        if (it.hasFocus) {
            state.preferTextInput(owner)
        }
    }.onKey(ComposeKey.Escape) {
        if (onEscape == null) {
            state.requestPlayerFocus()
        } else {
            onEscape()
        }
    }
}

@Immutable
data class ControllerVisibility(
    val topBar: Boolean,
    val bottomBar: Boolean,
    val floatingBottomEnd: Boolean,
    val rhsBar: Boolean,
    val gestureLock: Boolean,
    val detachedSlider: Boolean
) {
    companion object {
        @Stable
        val Visible = ControllerVisibility(
            topBar = true,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = true,
            gestureLock = true,
            detachedSlider = false,
        )

        @Stable
        val Invisible = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = true,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )

        @Stable
        val DetachedSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = true,
        )

        @Stable
        val InlineSliderOnly = ControllerVisibility(
            topBar = false,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )
    }
}

@Stable
class PlayerControllerState(
    initialVisibility: ControllerVisibility = DEFAULT_INITIAL_VISIBILITY
) {
    companion object {
        val DEFAULT_INITIAL_VISIBILITY = ControllerVisibility.Invisible
    }

    val focusState = PlayerFocusState()

    private var fullVisible by mutableStateOf(initialVisibility == ControllerVisibility.Visible)
    private val hasProgressBarRequester by derivedStateOf { progressBarRequesters.isNotEmpty() }
    private val hasInlineProgressSliderRequester by derivedStateOf {
        inlineProgressSliderRequesters.isNotEmpty()
    }

    val visibility: ControllerVisibility by derivedStateOf {

        if (hasInlineProgressSliderRequester) return@derivedStateOf ControllerVisibility.InlineSliderOnly
        if (alwaysOn) return@derivedStateOf ControllerVisibility.Visible
        if (fullVisible) return@derivedStateOf ControllerVisibility.Visible
        if (hasProgressBarRequester) return@derivedStateOf ControllerVisibility.DetachedSliderOnly
        ControllerVisibility.Invisible
    }

    fun toggleFullVisible(visible: Boolean? = null) {
        fullVisible = visible ?: !fullVisible
    }

    val setFullVisible: (visible: Boolean) -> Unit = {
        fullVisible = it
    }

    private val alwaysOnRequests = SnapshotStateList<Any>()

    val alwaysOn: Boolean by derivedStateOf {
        alwaysOnRequests.isNotEmpty()
    }

    fun setRequestAlwaysOn(requester: Any, isAlwaysOn: Boolean) {
        if (isAlwaysOn) {
            if (requester in alwaysOnRequests) return
            alwaysOnRequests.add(requester)
        } else {
            alwaysOnRequests.remove(requester)
        }
    }

    private val progressBarRequesters = SnapshotStateList<Any>()

    private val inlineProgressSliderRequesters = SnapshotStateList<Any>()

    fun setRequestInlineProgressSlider(requester: Any) {
        if (requester in inlineProgressSliderRequesters) return
        inlineProgressSliderRequesters.add(requester)
    }

    fun cancelRequestInlineProgressSlider(requester: Any) {
        inlineProgressSliderRequesters.remove(requester)
    }

    fun setRequestProgressBar(requester: Any) {
        if (requester in progressBarRequesters) return
        progressBarRequesters.add(requester)
    }

    fun cancelRequestProgressBarVisible(requester: Any) {
        progressBarRequesters.remove(requester)
    }

    @TestOnly
    fun getAlwaysOnRequesters(): List<Any> {
        return alwaysOnRequests
    }
}

interface AlwaysOnRequester {
    fun request()
    fun cancelRequest()
}

@Composable
fun rememberAlwaysOnRequester(
    controllerState: PlayerControllerState,
    debugName: String
): AlwaysOnRequester {
    val requester = remember(controllerState, debugName) {
        object : AlwaysOnRequester {
            override fun request() {
                controllerState.setRequestAlwaysOn(this, true)
            }

            override fun cancelRequest() {
                controllerState.setRequestAlwaysOn(this, false)
            }

            override fun toString(): String {
                return "AlwaysOnRequester($debugName)"
            }
        }
    }
    DisposableEffect(requester) {
        onDispose {
            requester.cancelRequest()
        }
    }
    return requester
}

fun Modifier.hoverToRequestAlwaysOn(
    requester: AlwaysOnRequester
): Modifier = hoverable(
    onHover = {
        requester.request()
    },
    onUnhover = {
        requester.cancelRequest()
    },
)
