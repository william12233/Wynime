@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")

package com.wynime.app.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import com.wynime.utils.io.SystemPath

expect val LocalContext: ProvidableCompositionLocal<Context>

expect abstract class Context

typealias ContextMP = Context

val Context.files: ContextFiles get() = filesImpl
internal expect val Context.filesImpl: ContextFiles

interface ContextFiles {
    val cacheDir: SystemPath

    val dataDir: SystemPath

    val defaultMediaCacheBaseDir: SystemPath
}
