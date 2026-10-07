package com.wynime.app.platform

interface AppTerminator {
    fun exitApp(context: ContextMP, status: Int): Nothing
}

expect val DefaultAppTerminator: AppTerminator
