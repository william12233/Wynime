package com.wynime.app.ui.framework

import javax.swing.SwingUtilities

fun <T> runOnSwingEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait {
        result = runCatching(block)
    }
    return checkNotNull(result) { "EDT block did not run" }.getOrThrow()
}
