package com.wynime.app.domain.mediasource.web

import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaSample
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals

@DisabledOnOs(
    value = [OS.WINDOWS],
    architectures = ["aarch64", "arm64", "arm"],
    disabledReason = "Microsoft onnxruntime doesn't support windows aarch64.",
)
class DesktopOnnxImageCaptchaRecognizerTest {
    @Test
    fun `recognizes a labeled real captcha sample`() = runTest {
        val bytes = checkNotNull(javaClass.getResourceAsStream(TEST_SAMPLE_RESOURCE)).use { it.readBytes() }
        val recognizer = DesktopOnnxImageCaptchaRecognizer()

        val result = recognizer.recognize(
            ImageCaptchaSample(
                bytes = bytes,
                mediaType = "image/png",
                sourceUrl = "resource://$TEST_SAMPLE_RESOURCE",
            ),
        )

        assertEquals("0000", result)
    }

    private companion object {
        private const val TEST_SAMPLE_RESOURCE = "/captcha-test/0000.png"
    }
}
