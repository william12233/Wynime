package com.wynime.app.ui.subject.episode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.media.player.ChunkState
import com.wynime.app.domain.media.player.staticMediaCacheProgressState
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.ui.episode.share.MediaShareData
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.effects.TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE
import com.wynime.app.ui.foundation.effects.TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE
import com.wynime.app.ui.framework.doesNotExist
import com.wynime.app.ui.framework.exists
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import com.wynime.app.videoplayer.ui.ControllerVisibility
import com.wynime.app.videoplayer.ui.MutablePlayerFullscreenState
import com.wynime.app.videoplayer.ui.NoOpPlaybackSpeedController
import com.wynime.app.videoplayer.ui.NoOpVideoAspectRatio
import com.wynime.app.videoplayer.ui.PlaybackSpeedControllerState
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.ui.VideoAspectRatioControllerState
import com.wynime.app.videoplayer.ui.gesture.GestureFamily
import com.wynime.app.videoplayer.ui.gesture.NoOpLevelController
import com.wynime.app.videoplayer.ui.gesture.VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION
import com.wynime.app.videoplayer.ui.progress.PlayerControllerDefaults
import com.wynime.app.videoplayer.ui.progress.PlayerProgressSliderState
import com.wynime.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER_PREVIEW_POPUP
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class EpisodeVideoCursorTest {

    private companion object {
        const val WAIT_TIMEOUT = 5_000L
    }

    private val controllerState = PlayerControllerState(ControllerVisibility.Invisible)
    private var currentPositionMillis by mutableLongStateOf(0L)
    private val progressSliderState: PlayerProgressSliderState = PlayerProgressSliderState(
        { currentPositionMillis },
        { 100_000 },
        { persistentListOf() },
        onPreview = {},
        onPreviewFinished = { currentPositionMillis = it },
    )

    private val SemanticsNodeInteractionsProvider.topBar
        get() = onNodeWithTag(TAG_EPISODE_VIDEO_TOP_BAR, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.previewPopup
        get() = onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true)

    private val SemanticsNodeInteractionsProvider.cursorVisible
        get() = onNodeWithTag(TAG_CURSOR_VISIBILITY_EFFECT_VISIBLE, useUnmergedTree = true)

    private val SemanticsNodeInteractionsProvider.cursorInvisible
        get() = onNodeWithTag(TAG_CURSOR_VISIBILITY_EFFECT_INVISIBLE, useUnmergedTree = true)

    @Composable
    private fun Player(gestureFamily: GestureFamily = GestureFamily.MOUSE) {
        ProvideCompositionLocalsForPreview(darkMode = DarkMode.DARK) {
            val scope = rememberCoroutineScope()
            val playerState = remember {
                TestMediampPlayer(scope.coroutineContext)
            }
            Row {
                val expanded = true
                val videoScaffoldConfig = VideoScaffoldConfig.Default
                val fullscreenState = remember { MutablePlayerFullscreenState(expanded) }
                val cacheProgressInfoFlow = staticMediaCacheProgressState(ChunkState.NONE).flow
                EpisodeVideoImpl(
                    playerState = playerState,
                    expanded = expanded,
                    hasNextEpisode = true,
                    onClickNextEpisode = {},
                    playerControllerState = controllerState,
                    title = { Text("Title") },
                    videoLoadingStateFlow = remember { MutableStateFlow(VideoLoadingState.Succeed) },
                    fullscreenState = fullscreenState,
                    onClickScreenshot = {},
                    detachedProgressSlider = {
                        PlayerControllerDefaults.MediaProgressSlider(
                            progressSliderState,
                            cacheProgressInfoFlow = cacheProgressInfoFlow,
                            enabled = false,
                        )
                    },
                    sidebarVisible = true,
                    onToggleSidebar = {},
                    progressSliderState = progressSliderState,
                    cacheProgressInfoFlow = cacheProgressInfoFlow,
                    audioController = NoOpLevelController,
                    brightnessController = NoOpLevelController,
                    playbackSpeedControllerState = remember {
                        PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
                    },
                    videoAspectRatioControllerState = remember {
                        VideoAspectRatioControllerState(NoOpVideoAspectRatio, scope)
                    },
                    leftBottomTips = {},
                    fullscreenSwitchButton = {
                        EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                            videoScaffoldConfig.fullscreenSwitchMode,
                            fullscreenState,
                        )
                    },
                    sideSheets = {},
                    shareData = MediaShareData(null, null),
                    onClickCache = {},
                    modifier = Modifier.weight(1f),
                    gestureFamily = gestureFamily,
                )

                Column(Modifier.fillMaxHeight().requiredWidth(100.dp)) {
                    Text("Dummy")
                }
            }
        }
    }

    @Test
    fun `initial controller visible`() = runWynimeComposeUiTest {
        controllerState.toggleFullVisible(true)
        setContent {
            Player()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
        }
    }

    @Test
    fun `initial controller invisible`() = runWynimeComposeUiTest {
        controllerState.toggleFullVisible(false)
        setContent {
            Player()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
        }
    }

    @Test
    fun `initial controller invisible and hover`() = runWynimeComposeUiTest {
        controllerState.toggleFullVisible(false)
        setContent {
            Player()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
        }
        runOnIdle {
            onRoot().performMouseInput {
                moveTo(center)
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorInvisible.exists() }
        }
    }

    @Test
    fun `show cursor when outside of video`() = runWynimeComposeUiTest {
        controllerState.toggleFullVisible(false)
        setContent {
            Player()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
        }
        runOnIdle {
            onRoot().performMouseInput {
                moveTo(center)
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorInvisible.exists() }
        }
        runOnIdle {
            onRoot().performMouseInput {
                moveTo(centerRight)
            }
        }
        runOnIdle {
            assertEquals(ControllerVisibility.Invisible, controllerState.visibility)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
        }
    }

    @Test
    fun `hide cursor after some seconds`() = runWynimeComposeUiTest {
        controllerState.toggleFullVisible(true)
        mainClock.autoAdvance = false
        setContent {
            Player(gestureFamily = GestureFamily.MOUSE)
        }
        val root = onAllNodes(isRoot()).onFirst()
        runOnIdle {
            assertEquals(true, controllerState.visibility.topBar)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
            root.performMouseInput {
                moveTo(centerRight)
            }
        }
        runOnIdle {
            root.performMouseInput {
                moveTo(center)
            }

            root.performTouchInput {
                swipe(center, center - Offset(1f, 1f))
            }
        }
        runOnIdle {
            assertEquals(true, controllerState.visibility.topBar)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorVisible.exists() }
        }
        runOnIdle {
            mainClock.advanceTimeBy((VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION + 1.seconds).inWholeMilliseconds)
            mainClock.autoAdvance = true
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { cursorInvisible.doesNotExist() }
            assertEquals(false, controllerState.visibility.topBar)
        }
    }
}
