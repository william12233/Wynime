package com.wynime.app.domain.sourceplugin

import java.net.URLClassLoader
import com.wynime.app.platform.Context
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import java.io.File

actual fun createSourcePluginLoader(context: Context): SourcePluginLoader = JvmSourcePluginLoader

private object JvmSourcePluginLoader : SourcePluginLoader {
    override fun load(
        artifact: SystemPath,
        entryClass: String,
        context: SourcePluginContext,
    ): LoadedSourcePlugin {
        val classLoader = URLClassLoader(
            arrayOf(File(artifact.absolutePath).toURI().toURL()),
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
                releaseClassLoader = { classLoader.close() },
            )
        } catch (e: Throwable) {
            classLoader.close()
            throw e
        }
    }
}
