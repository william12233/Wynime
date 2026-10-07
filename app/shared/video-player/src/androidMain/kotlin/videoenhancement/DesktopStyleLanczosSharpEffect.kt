@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wynime.app.videoplayer.videoenhancement

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.wynime.utils.video.enhancement.shader.provider.VideoEnhancementShaderProvider
import kotlin.math.roundToInt

internal class DesktopStyleLanczosSharpEffect(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DesktopStyleLanczosSharpShaderProgram(context, viewportWidth, viewportHeight)
}

private class DesktopStyleLanczosSharpShaderProgram(
    context: Context,
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : BaseGlShaderProgram(
                                            true,
                                1,
) {
    val shaderSources = LanczosSharpShaderSources(context)

    private val program = try {
        GlProgram(shaderSources.vertexShader, shaderSources.fragmentShader).also {
            it.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException("Could not compile desktop-style Lanczos sharp effect", e)
    }

    private var inputWidth = 0
    private var inputHeight = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        val scale = minOf(
            viewportWidth.toDouble() / inputWidth,
            viewportHeight.toDouble() / inputHeight,
        )
        return Size(
            (inputWidth * scale).roundToInt().coerceAtLeast(1),
            (inputHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId,                      0)
            program.setFloatsUniform(
                "uInputSize",
                floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
            )
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,               0,               4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release desktop-style Lanczos sharp effect", e)
        }
        super.release()
    }
}

private class LanczosSharpShaderSources(context: Context) {
    val vertexShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/ewa_lanczossharp.vert",
    )
    val fragmentShader = VideoEnhancementShaderProvider.getShaderSource(
        context,
        "$exoEffectShaderDirectory/ewa_lanczossharp.frag",
    )
}