/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import me.him188.ani.source.plugin.api.SourcePluginEntryPoint
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.app.platform.Context as AppContext

actual fun createSourcePluginLoader(context: AppContext): SourcePluginLoader = AndroidSourcePluginLoader(context)

private class AndroidSourcePluginLoader(
    private val context: Context,
) : SourcePluginLoader {
    override fun load(
        artifact: SystemPath,
        entryClass: String,
        context: SourcePluginContext,
    ): LoadedSourcePlugin {
        val artifactFile = File(artifact.absolutePath)
        check(artifactFile.isFile) { "Source plugin artifact does not exist: ${artifact.absolutePath}" }
        check(artifactFile.setWritable(false, false) && !artifactFile.canWrite()) {
            "Source plugin artifact must be read-only before Android class loading: ${artifact.absolutePath}"
        }
        val optimizedDirectory = File(this.context.codeCacheDir ?: this.context.cacheDir, "source-plugins")
            .also(File::mkdirs)
        val classLoader = DexClassLoader(
            artifactFile.absolutePath,
            optimizedDirectory.absolutePath,
            null,
            SourcePluginEntryPoint::class.java.classLoader,
        )
        try {
            val entryPoint = Class.forName(entryClass, true, classLoader)
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance() as? SourcePluginEntryPoint
                ?: error("$entryClass does not implement SourcePluginEntryPoint")
            return LoadedSourcePlugin(
                plugin = entryPoint.create(context),
                releaseClassLoader = {},
            )
        } catch (e: Throwable) {
            throw e
        }
    }
}
