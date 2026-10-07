package com.wynime.app.domain.mediasource.web

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaSample
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidOnnxImageCaptchaRecognizerTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `recognizes a labeled real captcha sample`() = runBlocking {
        val bytes = context.assets.open(TEST_SAMPLE_ASSET).use { it.readBytes() }
        val recognizer = AndroidOnnxImageCaptchaRecognizer()

        val result = recognizer.recognize(
            ImageCaptchaSample(
                bytes = bytes,
                mediaType = "image/png",
                sourceUrl = "asset://$TEST_SAMPLE_ASSET",
            ),
        )

        assertEquals("0000", result)
    }

    private companion object {
        private const val TEST_SAMPLE_ASSET = "captcha-test/0000.png"
    }
}
