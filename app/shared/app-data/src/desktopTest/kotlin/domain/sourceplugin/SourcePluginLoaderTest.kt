package com.wynime.app.domain.sourceplugin

import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.runBlocking
import com.wynime.app.platform.Context
import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceConnectionState
import com.wynime.source.plugin.api.SourceConnectionStatus
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginMetadata
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.resolve
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePluginLoaderTest {
    private val root = SystemPaths.createTempDirectory("source-plugin-loader-test")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `desktop loader instantiates executable plugin jar`() = runBlocking {
        val artifact = root.resolve("plugin.jar")
        createFixtureJar(artifact.absolutePath)

        val context = object : SourcePluginContext {
            override val pluginId = "fixture"
            override val hostVersion = "test"
            override val platform = SourcePluginPlatform.DESKTOP
            override val http: SourceHttpClient = object : SourceHttpClient {
                override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse =
                    error("HTTP is not used by the loader fixture")
            }
            override val logger: SourcePluginLogger = object : SourcePluginLogger {
                override fun debug(message: String) = Unit
                override fun info(message: String) = Unit
                override fun warn(message: String, throwable: Throwable?) = Unit
                override fun error(message: String, throwable: Throwable?) = Unit
            }
        }
        val loader = createSourcePluginLoader(object : Context() {})
        val loaded = loader.load(
            artifact = artifact,
            entryClass = "com.wynime.app.domain.sourceplugin.LoaderFixtureEntryPoint",
            context = context,
        )

        try {
            assertEquals("fixture", loaded.plugin.metadata.id)
            assertEquals("Fixture plugin", loaded.plugin.metadata.displayName)
            assertEquals(SourceConnectionState.CONNECTED, loaded.plugin.checkConnection().state)
        } finally {
            loaded.close()
        }
    }

    private fun createFixtureJar(path: String) {
        val classLoader = javaClass.classLoader
        JarOutputStream(FileOutputStream(File(path))).use { output ->
            listOf(
                "com/wynime/app/domain/sourceplugin/LoaderFixtureEntryPoint.class",
                "com/wynime/app/domain/sourceplugin/LoaderFixturePlugin.class",
            ).forEach { resource ->
                output.putNextEntry(JarEntry(resource))
                classLoader.getResourceAsStream(resource).use { input ->
                    checkNotNull(input) { "Missing test fixture class $resource" }.copyTo(output)
                }
                output.closeEntry()
            }
        }
    }
}

class LoaderFixtureEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = LoaderFixturePlugin(context)
}

private class LoaderFixturePlugin(
    private val context: SourcePluginContext,
) : SourcePlugin {
    override val metadata = SourcePluginMetadata(
        id = "fixture",
        displayName = "Fixture plugin",
        version = "1.0.0",
        website = "https://fixture.invalid",
        pluginApiVersion = SOURCE_PLUGIN_API_VERSION,
        minHostVersion = "0",
        supportedPlatforms = setOf(SourcePluginPlatform.DESKTOP),
    )

    override suspend fun checkConnection(): SourceConnectionStatus =
        SourceConnectionStatus(SourceConnectionState.CONNECTED)

    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> = emptyList()

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails =
        error("Subject lookup is not used by the loader fixture")

    override suspend fun resolve(request: SourceResolveRequest): ResolvedMedia = ResolvedMedia(
        stableIdentity = context.pluginId,
        url = "https://fixture.invalid/video.mp4",
        format = ResolvedMediaFormat.MP4,
    )

    override fun close() = Unit
}
