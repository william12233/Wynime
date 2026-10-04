/*
 * Copyright (C) 2026 OpenAni contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.domain.sourceplugin

import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.platform.Context
import me.him188.ani.source.plugin.api.SourceHttpClient
import me.him188.ani.source.plugin.api.SourceHttpRequest
import me.him188.ani.source.plugin.api.SourceHttpResponse
import me.him188.ani.source.plugin.api.SourcePluginPlatform
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.source.plugin.api.SourcePluginLogger
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePluginInstallerTest {
    private val root = SystemPaths.createTempDirectory("source-plugin-installer-test")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `install stages, verifies, loads, and records a real desktop jar`() = runTest {
        val artifact = createFixtureArtifact()
        val entry = fixtureEntry("1.0.0")
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val installer = createInstaller(repository, entry, artifact)
        var validated = false

        val installed = installer.install(entry) { candidate ->
            validated = true
            assertTrue(File(candidate.artifactPath).isFile)
            val loaded = createSourcePluginLoader(object : Context() {}).load(
                artifact = Path(candidate.artifactPath).inSystem,
                entryClass = candidate.manifest.entryClass,
                context = fixtureContext(),
            )
            try {
                assertEquals("fixture", loaded.plugin.metadata.id)
            } finally {
                loaded.close()
            }
        }

        assertTrue(validated)
        assertEquals("fixture", repository.snapshot().plugins.single().id)
        assertEquals(installed.version, repository.snapshot().plugins.single().version)
        assertTrue(File(installed.artifactPath).isFile)
        assertTrue(root.resolve("installed").resolve("fixture").exists())
        assertTrue(File(root.resolve("staging").absolutePath).listFiles().orEmpty().isEmpty())

        installer.uninstall("fixture")
        assertTrue(repository.snapshot().plugins.isEmpty())
        assertFalse(root.resolve("installed").resolve("fixture").resolve("1.0.0").exists())
    }

    @Test
    fun `hash failure removes staging and never records a plugin`() = runTest {
        val artifact = createFixtureArtifact()
        val entry = fixtureEntry("1.0.0")
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val installer = createInstaller(repository, entry, artifact, manifestSha256 = "0".repeat(64))

        assertFailsWith<IllegalStateException> { installer.install(entry) }

        assertTrue(repository.snapshot().plugins.isEmpty())
        assertFalse(root.resolve("installed").resolve("fixture").exists())
        assertTrue(File(root.resolve("staging").absolutePath).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `failed update preserves the previous installed version`() = runTest {
        val artifact = createFixtureArtifact()
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val first = fixtureEntry("1.0.0")
        val firstInstaller = createInstaller(repository, first, artifact)
        val previous = firstInstaller.install(first)
        val update = fixtureEntry("1.0.1")
        val updateInstaller = createInstaller(repository, update, artifact)

        assertFailsWith<IllegalStateException> {
            updateInstaller.install(update) { error("fixture load validation failed") }
        }

        val current = repository.snapshot().plugins.single()
        assertEquals("1.0.0", current.version)
        assertTrue(File(previous.artifactPath).isFile)
        assertFalse(root.resolve("installed").resolve("fixture").resolve("1.0.1").exists())
        assertTrue(File(root.resolve("staging").absolutePath).listFiles().orEmpty().isEmpty())
    }

    private fun createInstaller(
        repository: InstalledSourcePluginRepository,
        entry: SourcePluginIndexEntry,
        artifact: ByteArray,
        manifestSha256: String = sha256Hex(artifact),
    ): SourcePluginInstaller {
        val http = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/manifests/fixture.json" to ArrayDeque(
                    listOf(response(manifestJson(entry, manifestSha256))),
                ),
                "https://repo.example/artifacts/fixture.jar" to ArrayDeque(
                    listOf(SourceHttpResponse(200, "https://repo.example/artifacts/fixture.jar", body = artifact)),
                ),
            ),
        )
        return SourcePluginInstaller(
            repositoryClient = SourcePluginRepositoryClient(http, baseUrl = "https://repo.example"),
            installedRepository = repository,
            storage = SourcePluginStorage(root),
            platform = SourcePluginPlatform.DESKTOP,
            hostVersion = "1.0.0",
        )
    }

    private fun fixtureEntry(version: String) =
        SourcePluginIndexEntry(
            id = "fixture",
            displayName = "Fixture plugin",
            version = version,
            website = "https://fixture.invalid",
            platforms = setOf(SourcePluginPlatform.DESKTOP),
            manifest = "manifests/fixture.json",
        )

    private fun manifestJson(entry: SourcePluginIndexEntry, artifactSha256: String): String =
        """
        {
          "id":"${entry.id}","name":"Fixture plugin","version":"${entry.version}",
          "pluginApiVersion":1,"minHostVersion":"1.0.0",
          "entryClass":"me.him188.ani.app.domain.sourceplugin.LoaderFixtureEntryPoint",
          "website":"https://fixture.invalid","platforms":["desktop"],
          "artifacts":{"desktop":{"url":"artifacts/fixture.jar","sha256":"$artifactSha256","format":"jar"}}
        }
        """.trimIndent().replace("\n", "")

    private fun response(body: String) = SourceHttpResponse(
        statusCode = 200,
        finalUrl = "https://repo.example/manifests/fixture.json",
        body = body.encodeToByteArray(),
    )

    private fun createFixtureArtifact(): ByteArray {
        val artifact = root.resolve("fixture.jar")
        JarOutputStream(FileOutputStream(File(artifact.absolutePath))).use { output ->
            listOf(
                "me/him188/ani/app/domain/sourceplugin/LoaderFixtureEntryPoint.class",
                "me/him188/ani/app/domain/sourceplugin/LoaderFixturePlugin.class",
            ).forEach { resource ->
                output.putNextEntry(JarEntry(resource))
                javaClass.classLoader.getResourceAsStream(resource).use { input ->
                    checkNotNull(input) { "Missing test fixture class $resource" }.copyTo(output)
                }
                output.closeEntry()
            }
        }
        return File(artifact.absolutePath).readBytes()
    }

    private fun fixtureContext() = object : SourcePluginContext {
        override val pluginId = "fixture"
        override val hostVersion = "1.0.0"
        override val platform = SourcePluginPlatform.DESKTOP
        override val http: SourceHttpClient = object : SourceHttpClient {
            override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse =
                error("HTTP is not used by the installer fixture")
        }
        override val logger: SourcePluginLogger = object : SourcePluginLogger {
            override fun debug(message: String) = Unit
            override fun info(message: String) = Unit
            override fun warn(message: String, throwable: Throwable?) = Unit
            override fun error(message: String, throwable: Throwable?) = Unit
        }
    }

    private class FakeSourceHttpClient(
        responses: Map<String, ArrayDeque<SourceHttpResponse>>,
    ) : SourceHttpClient {
        private val responses = responses.toMutableMap()

        override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse =
            responses[request.url]?.removeFirstOrNull()
                ?: error("No fake response for ${request.url}")
    }
}
