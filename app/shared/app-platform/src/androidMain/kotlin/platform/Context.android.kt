package com.wynime.app.platform

import android.os.Environment
import androidx.compose.runtime.ProvidableCompositionLocal
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.resolve
import com.wynime.utils.io.toKtPath
import java.io.File

actual typealias Context = android.content.Context

actual val LocalContext: ProvidableCompositionLocal<Context>
    get() = androidx.compose.ui.platform.LocalContext

class AndroidContextFiles(context: android.content.Context) : ContextFiles {
    override val cacheDir: SystemPath =
        (context.cacheDir ?: File("")).toKtPath().inSystem
    override val dataDir: SystemPath =
        (context.filesDir ?: File("")).toKtPath().inSystem

    val fallbackInternalBaseMediaCacheDir = dataDir.resolve("media-downloads")

    override val defaultMediaCacheBaseDir: SystemPath =
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.toKtPath()?.inSystem
            ?: fallbackInternalBaseMediaCacheDir
}

internal actual val Context.filesImpl: ContextFiles
    get() = AndroidContextFiles(this)

