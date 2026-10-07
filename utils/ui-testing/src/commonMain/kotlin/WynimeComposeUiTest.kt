package com.wynime.app.ui.framework

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.runComposeUiTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

expect fun runWynimeComposeUiTest(
    effectContext: CoroutineContext = EmptyCoroutineContext,
    testBody: WynimeComposeUiTest.() -> Unit
)

typealias WynimeComposeUiTest = ComposeUiTest

inline val WynimeComposeUiTest.composeUiTest get() = this

@Deprecated(
    "This function may be affected by different window sizes. Use assertScreenshot on node instead.",
    ReplaceWith("onNodeWithTag(\"YOUR_TAG\").assertScreenshot(expectedResource)"),
    level = DeprecationLevel.ERROR,
)
expect fun WynimeComposeUiTest.assertScreenshot(expectedResource: String)

expect fun ImageBitmap.assertScreenshot(expectedResource: String)

expect fun SemanticsNodeInteraction.assertScreenshot(expectedResource: String)
