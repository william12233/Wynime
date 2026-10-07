package com.wynime.app.ui.framework

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes

@Deprecated(
    "This function may be affected by different window sizes. Use assertScreenshot on node instead.",
    ReplaceWith("onNodeWithTag(\"YOUR_TAG\").assertScreenshot(expectedResource)"),
    level = DeprecationLevel.ERROR,
)
actual fun WynimeComposeUiTest.assertScreenshot(expectedResource: String) {
}

actual fun ImageBitmap.assertScreenshot(expectedResource: String) {
}

actual fun SemanticsNodeInteraction.assertScreenshot(expectedResource: String) {
}

@OptIn(DelicateCoroutinesApi::class)
actual fun runWynimeComposeUiTest(effectContext: CoroutineContext, testBody: WynimeComposeUiTest.() -> Unit) {

    Dispatchers.resetMain()

    val testThread = Thread.currentThread()
    var timedOut = false
    val job = GlobalScope.launch {
        delay(1.minutes)
        timedOut = true
        testThread.interrupt()
    }

    runComposeUiTest(effectContext = effectContext) {
        try {
            testBody()
        } catch (e: InterruptedException) {
            if (timedOut) {
                throw AssertionError("Test timed out after 1 minute")
            } else {
                throw e
            }
        } catch (e: Throwable) {

            try {
                onRoot().assertScreenshot("")
            } catch (extraInfo: Throwable) {
                e.addSuppressed(extraInfo)
                throw e
            }
            throw e
        } finally {
            job.cancel()
        }
    }
}
