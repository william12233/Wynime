@file:Suppress("NOTHING_TO_INLINE")

package com.wynime.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.window.WindowState
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.resolve
import com.wynime.utils.io.toKtPath
import java.io.File
import kotlin.contracts.contract

actual abstract class Context

@Stable
class DesktopContext(
    val windowState: WindowState,
    val dataDir: File,
    val cacheDir: File,
    val logsDir: File,
    val extraWindowProperties: ExtraWindowProperties,
) : Context() {
    val dataStoreDir = dataDir.resolve("datastore")
}

@Stable
class ExtraWindowProperties

actual val LocalContext: ProvidableCompositionLocal<Context> = compositionLocalOf {
    error("No Context provided")
}

object LocalDesktopContext {
    val current: DesktopContext
        @Composable
        inline get() {
            val context = LocalContext.current
            check(context is DesktopContext)
            return context
        }
}

@Stable
inline fun Context.checkIsDesktop(): DesktopContext {
    contract { returns() implies (this@checkIsDesktop is DesktopContext) }
    check(this is DesktopContext) { "Context must be DesktopContext, but had: $this" }
    return this
}

internal actual val Context.filesImpl: ContextFiles
    get() = object : ContextFiles {
        override val cacheDir: SystemPath = (this@filesImpl as DesktopContext).cacheDir.toKtPath().inSystem
        override val dataDir: SystemPath = (this@filesImpl as DesktopContext).dataDir.toKtPath().inSystem

        override val defaultMediaCacheBaseDir: SystemPath = dataDir.resolve("media-downloads")
    }
