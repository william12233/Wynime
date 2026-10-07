package com.wynime.app.domain.mediasource.web

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaSample
import com.wynime.app.domain.mediasource.web.captcha.readImageCaptchaModelBytes
import com.wynime.utils.coroutines.SuspendLazy
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.FloatBuffer
import javax.imageio.ImageIO

class DesktopOnnxImageCaptchaRecognizer : ImageCaptchaRecognizer {
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OrtEnvironment.getEnvironment()
    }
    private val session = SuspendLazy {
        val model = readImageCaptchaModelBytes()
        OrtSession.SessionOptions().use { options ->
            environment.createSession(model, options).also {
                logger.info { "Loaded image captcha model" }
            }
        }
    }

    override suspend fun recognize(sample: ImageCaptchaSample): String? = withContext(Dispatchers.Default) {
        val session = session.get()
        runCatching { recognizeBlocking(session, sample.bytes) }
            .onSuccess { answer ->
                logger.debug { "Image captcha recognition result for ${sample.sourceUrl}: ${answer ?: "none"}" }
            }
            .onFailure { exception ->
                logger.warn(exception) { "Failed to recognize image captcha from ${sample.sourceUrl}" }
            }
            .getOrNull()
    }

    private fun recognizeBlocking(session: OrtSession, bytes: ByteArray): String? {
        val input = preprocess(bytes) ?: return null
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 1, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong()),
        ).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { result ->
                val logits = readLogits(result)
                if (logits.size != CAPTCHA_LENGTH || logits.any { it.size != DIGIT_COUNT }) {
                    return null
                }
                return logits.joinToString(separator = "") { positionLogits ->
                    positionLogits.indices.maxBy { positionLogits[it] }.toString()
                }
            }
        }
    }

    private fun preprocess(bytes: ByteArray): FloatArray? {
        val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val resized = BufferedImage(INPUT_WIDTH, INPUT_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = resized.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            graphics.drawImage(source, 0, 0, INPUT_WIDTH, INPUT_HEIGHT, null)
        } finally {
            graphics.dispose()
        }
        return FloatArray(INPUT_WIDTH * INPUT_HEIGHT) { index ->
            val x = index % INPUT_WIDTH
            val y = index / INPUT_WIDTH
            val color = resized.getRGB(x, y)
            val red = color shr 16 and 0xff
            val green = color shr 8 and 0xff
            val blue = color and 0xff
            val gray = (299 * red + 587 * green + 114 * blue + 500) / 1000
            gray / 255f
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readLogits(result: OrtSession.Result): Array<FloatArray> {
        val batch = result[OUTPUT_NAME].get().value as Array<Array<FloatArray>>
        return batch.single()
    }

    private companion object {
        private val logger = logger<DesktopOnnxImageCaptchaRecognizer>()

        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "logits"
        private const val INPUT_WIDTH = 96
        private const val INPUT_HEIGHT = 32
        private const val CAPTCHA_LENGTH = 4
        private const val DIGIT_COUNT = 10
    }
}
