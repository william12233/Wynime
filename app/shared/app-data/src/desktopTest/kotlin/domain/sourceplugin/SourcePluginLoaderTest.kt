/*
 * Copyright (C) 2026 OpenAni contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.domain.sourceplugin

import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.platform.Context
import me.him188.ani.source.plugin.api.ResolvedMedia
import me.him188.ani.source.plugin.api.ResolvedMediaFormat
import me.him188.ani.source.plugin.api.SourceConnectionState
import me.him188.ani.source.plugin.api.SourceConnectionStatus
import me.him188.ani.source.plugin.api.SourceHttpClient
import me.him188.ani.source.plugin.api.SourceHttpRequest
import me.him188.ani.source.plugin.api.SourceHttpResponse
import me.him188.ani.source.plugin.api.SourcePlugin
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.source.plugin.api.SourcePluginEntryPoint
import me.him188.ani.source.plugin.api.SourcePluginLogger
import me.him188.ani.source.plugin.api.SourcePluginMetadata
import me.him188.ani.source.plugin.api.SourcePluginPlatform
import me.him188.ani.source.plugin.api.SourceResolveRequest
import me.him188.ani.source.plugin.api.SourceSearchRequest
import me.him188.ani.source.plugin.api.SourceSubject
import me.him188.ani.source.plugin.api.SourceSubjectDetails
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
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
            entryClass = "me.him188.ani.app.domain.sourceplugin.LoaderFixtureEntryPoint",
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
                "me/him188/ani/app/domain/sourceplugin/LoaderFixtureEntryPoint.class",
                "me/him188/ani/app/domain/sourceplugin/LoaderFixturePlugin.class",
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
        pluginApiVersion = 1,
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
