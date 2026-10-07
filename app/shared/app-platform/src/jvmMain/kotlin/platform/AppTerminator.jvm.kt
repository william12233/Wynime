package com.wynime.app.platform

import kotlin.system.exitProcess

actual val DefaultAppTerminator: AppTerminator get() = NativeAppTerminator

private object NativeAppTerminator : AppTerminator {
    override fun exitApp(context: ContextMP, status: Int): Nothing {
        exitProcess(status)
    }
}
