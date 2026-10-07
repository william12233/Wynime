package com.wynime.app.ui.framework

import androidx.compose.ui.test.SkikoComposeUiTest

@Deprecated(
    "This function may be affected by different window sizes. Use assertScreenshot on node instead.",
    ReplaceWith("onNodeWithTag(\"YOUR_TAG\").assertScreenshot(expectedResource)"),
    level = DeprecationLevel.ERROR,
)
actual fun WynimeComposeUiTest.assertScreenshot(expectedResource: String) {
    return (this.composeUiTest as SkikoComposeUiTest).assertScreenshot(expectedResource)
}
