package com.wynime.app.videoplayer.videoenhancement

import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.utils.platform.currentPlatformDesktop
import com.wynime.utils.platform.isWindows
import com.wynime.utils.video.enhancement.shader.provider.VideoEnhancementShaderProvider
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MPVHandle
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

actual fun createVideoEnhancementController(
    player: MediampPlayer,
    playerKernelConfig: Flow<PlayerKernelConfig>,
    parentCoroutineContext: CoroutineContext,
): VideoEnhancementController? {

    val handle = player.impl as? MPVHandle ?: return null
    return MpvVideoEnhancementController(player, handle, parentCoroutineContext)
}

private class MpvVideoEnhancementController(
    player: MediampPlayer,
    private val handle: MPVHandle,
    parentCoroutineContext: CoroutineContext,
) : BaseVideoEnhancementController(player, parentCoroutineContext) {
    private val lock = Any()
    private val originalProperties = enhancementPropertyNames.associateWith { name ->
        checkNotNull(handle.getPropertyString(name)) { "mpv property is unavailable: $name" }
    }
    private val clearProperties = if (currentPlatformDesktop().isWindows()) {
        windowsLiteClearProperties
    } else {
        fullClearProperties
    }

    private var effectiveMode: VideoEnhancementMode = VideoEnhancementMode.OFF
    private val appliedAnime4kShaders = mutableListOf<Path>()

    init {
        startObserving()
    }

    override suspend fun apply(
        mode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ) {
        val targetMode = getEffectiveMode(mode, videoSize, viewportSize)
        if (synchronized(lock) { targetMode == effectiveMode }) return

        val shaderPaths = when (targetMode) {
            VideoEnhancementMode.OFF -> emptyList()
            VideoEnhancementMode.PERFORMANCE -> listOf(
                VideoEnhancementShaderProvider.getShaderPath(anime4kRestoreShaderName),
            )

            VideoEnhancementMode.QUALITY -> anime4kQualityShaderNames.map {
                VideoEnhancementShaderProvider.getShaderPath(it)
            }
        }

        synchronized(lock) {
            applyEffectiveModeLocked(targetMode, shaderPaths)
        }
    }

    private fun getEffectiveMode(
        requestedMode: VideoEnhancementMode,
        videoSize: VideoDimensions?,
        viewportSize: VideoDimensions?,
    ): VideoEnhancementMode {
        val needsUpscale = videoSize != null && viewportSize != null &&
                minOf(
                    viewportSize.width.toDouble() / videoSize.width,
                    viewportSize.height.toDouble() / videoSize.height,
                ) > 1.0
        return if (requestedMode != VideoEnhancementMode.OFF && needsUpscale) {
            requestedMode
        } else {
            VideoEnhancementMode.OFF
        }
    }

    private fun applyEffectiveModeLocked(
        targetMode: VideoEnhancementMode,
        shaderPaths: List<Path>,
    ) {
        if (targetMode != effectiveMode) {
            removeAnime4kShadersLocked()
            val properties = when (targetMode) {
                VideoEnhancementMode.OFF -> originalProperties
                VideoEnhancementMode.PERFORMANCE,
                VideoEnhancementMode.QUALITY,
                    -> clearProperties
            }
            properties.forEach { (name, value) ->
                check(handle.setPropertyString(name, value)) {
                    "mpv rejected video enhancement property $name=$value"
                }
            }
            shaderPaths.forEach(::applyAnime4kShaderLocked)
            effectiveMode = targetMode
        }
    }

    private fun applyAnime4kShaderLocked(shaderPath: Path) {
        val absolutePath = shaderPath.toAbsolutePath()
        check(handle.command("change-list", "glsl-shaders", "append", absolutePath.toString())) {
            "mpv rejected Anime4K shader: $absolutePath"
        }
        appliedAnime4kShaders.add(absolutePath)
    }

    private fun removeAnime4kShadersLocked() {
        appliedAnime4kShaders.asReversed().forEach { shaderPath ->
            check(handle.command("change-list", "glsl-shaders", "remove", shaderPath.toString())) {
                "mpv failed to remove Anime4K shader: $shaderPath"
            }
        }
        appliedAnime4kShaders.clear()
    }

    override fun restore() {
        synchronized(lock) {
            appliedAnime4kShaders.asReversed().forEach { shaderPath ->
                runCatching {
                    handle.command("change-list", "glsl-shaders", "remove", shaderPath.toString())
                }
            }
            appliedAnime4kShaders.clear()
            originalProperties.forEach { (name, value) ->
                runCatching { handle.setPropertyString(name, value) }
            }
            effectiveMode = VideoEnhancementMode.OFF
        }
    }
}

private const val anime4kRestoreShaderName = "Anime4K_Restore_CNN_S.glsl"

private val anime4kQualityShaderNames = listOf(
    "Anime4K_Clamp_Highlights.glsl",
    "Anime4K_Restore_CNN_VL.glsl",
    "Anime4K_Upscale_CNN_x2_VL.glsl",
    "Anime4K_AutoDownscalePre_x2.glsl",
    "Anime4K_AutoDownscalePre_x4.glsl",
    "Anime4K_Upscale_CNN_x2_M.glsl",
)

private val enhancementPropertyNames = listOf(
    "correct-downscaling",
    "linear-downscaling",
    "sigmoid-upscaling",
    "scale",
    "dscale",
    "cscale",
    "scale-antiring",
    "dscale-antiring",
    "deband",
    "deband-iterations",
    "deband-threshold",
    "deband-range",
    "deband-grain",
)

private val fullClearProperties = mapOf(
    "correct-downscaling" to "yes",
    "linear-downscaling" to "yes",
    "sigmoid-upscaling" to "yes",
    "scale" to "ewa_lanczossharp",
    "dscale" to "ewa_lanczossharp",
    "cscale" to "ewa_lanczossharp",
    "scale-antiring" to "0.7",
    "dscale-antiring" to "0.7",
    "deband" to "yes",
    "deband-iterations" to "1",
    "deband-threshold" to "32",
    "deband-range" to "16",
    "deband-grain" to "0",
)

private val windowsLiteClearProperties = fullClearProperties + ("deband" to "no")
