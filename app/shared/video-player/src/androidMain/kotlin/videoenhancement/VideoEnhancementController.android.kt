@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wynime.app.videoplayer.videoenhancement

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.PlayerKernelConfig
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.CoroutineContext

actual fun createVideoEnhancementController(
    player: MediampPlayer,
    playerKernelConfig: Flow<PlayerKernelConfig>,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController? {
    val exoPlayer = player.impl as? ExoPlayer ?: return null
    return ExoPlayerVideoEnhancementController(
        player,
        exoPlayer,
        playerKernelConfig.map { it.exoPlayerInitEffectGraphInAdvance },
        parentCoroutineContext,
    )
}

private class ExoPlayerVideoEnhancementController(
    player: MediampPlayer,
    private val exoPlayer: ExoPlayer,
    preinitVideoEffects: Flow<Boolean>,
    parentCoroutineContext: CoroutineContext,
) : BaseVideoEnhancementController(player, parentCoroutineContext) {
    private var appliedMode = VideoEnhancementMode.OFF
    private var scalerApplied = false
    private var appliedWidth = 0
    private var appliedHeight = 0

    init {

        scope.launch {
            if (preinitVideoEffects.first()) {
                exoPlayer.setVideoEffects(emptyList())
            }
        }
        startObserving()
    }

    override suspend fun apply(
        mode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ) {
        if (mode == VideoEnhancementMode.OFF) {
            restore()
            return
        }

        val shouldApplyScaler = videoSize != null && viewportSize != null
        if (
            appliedMode == mode && scalerApplied == shouldApplyScaler &&
            (!shouldApplyScaler || appliedWidth == viewportSize.width && appliedHeight == viewportSize.height)
        ) return

        exoPlayer.setVideoEffects(
            buildList {
                when (mode) {
                    VideoEnhancementMode.OFF -> Unit
                    VideoEnhancementMode.PERFORMANCE -> add(Anime4kRestoreEffect)
                    VideoEnhancementMode.QUALITY -> {
                        add(Anime4kRestoreQualityEffect)
                        add(Anime4kUpscaleQualityEffect)
                    }
                }
                if (shouldApplyScaler) {
                    add(DesktopStyleLanczosSharpEffect(viewportSize.width, viewportSize.height))
                }
            },
        )
        appliedMode = mode
        scalerApplied = shouldApplyScaler
        appliedWidth = if (shouldApplyScaler) viewportSize.width else 0
        appliedHeight = if (shouldApplyScaler) viewportSize.height else 0
    }

    override fun restore() {
        if (appliedMode == VideoEnhancementMode.OFF) return
        exoPlayer.setVideoEffects(emptyList())
        appliedMode = VideoEnhancementMode.OFF
        scalerApplied = false
        appliedWidth = 0
        appliedHeight = 0
    }
}

internal const val exoEffectShaderDirectory = "exo-effects"