package com.wynime.app.ui.framework

internal actual fun Throwable.guessTestFunctionName(): String? {

    val runTest = stackTrace.indexOfFirst { it.methodName.contains("runAniComposeUiTest") }
    if (runTest == -1) return null
    val testFunction = stackTrace.getOrNull(runTest - 1) ?: return null
    return testFunction.className.substringAfterLast(".") + "." + testFunction.methodName.substringBefore("$")
}
