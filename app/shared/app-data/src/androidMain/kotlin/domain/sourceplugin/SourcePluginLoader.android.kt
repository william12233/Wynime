package com.wynime.app.domain.sourceplugin

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import com.wynime.app.platform.Context as AppContext

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
        check(ZipFile(artifactFile).use { it.getEntry("classes.dex") != null }) {
            "Source plugin artifact must contain classes.dex: ${artifactFile.name}"
        }
        val artifactDigest = sha256(artifactFile)
        val optimizedDirectory = File(
            this.context.codeCacheDir ?: this.context.cacheDir,
            "source-plugins/$artifactDigest",
        ).also { directory ->
            check(directory.mkdirs() || directory.isDirectory) {
                "Unable to create source plugin optimized directory"
            }
        }
        val hostClassLoader = this.context.classLoader
            ?: SourcePluginEntryPoint::class.java.classLoader
            ?: error("Host classloader is unavailable")
        val classLoader = DexClassLoader(
            artifactFile.absolutePath,
            optimizedDirectory.absolutePath,
            null,
            hostClassLoader,
        )
        val entryPointClass = Class.forName(entryClass, true, classLoader)
        check(SourcePluginEntryPoint::class.java.isAssignableFrom(entryPointClass)) {
            "Source plugin entry point $entryClass is not compatible with host SourcePluginEntryPoint " +
                "(entryClassLoader=${entryPointClass.classLoader}, hostClassLoader=$hostClassLoader)"
        }
        val entryPoint = entryPointClass
            .getDeclaredConstructor()
                .apply { isAccessible = true }
            .newInstance() as SourcePluginEntryPoint
        return LoadedSourcePlugin(
            plugin = entryPoint.create(context),
            releaseClassLoader = {},
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
