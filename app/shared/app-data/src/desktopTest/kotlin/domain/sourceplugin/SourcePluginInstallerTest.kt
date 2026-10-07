package com.wynime.app.domain.sourceplugin

import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.platform.Context
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.resolve
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
    fun `same-version failed validation is repaired from a fresh artifact`() = runTest {
        val artifact = createFixtureArtifact()
        val entry = fixtureEntry("1.0.0")
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val first = createInstaller(repository, entry, artifact).install(entry)
        var validationCalls = 0

        val repaired = createInstaller(repository, entry, artifact).install(entry) { candidate ->
            validationCalls++
            if (validationCalls == 1) {
                assertEquals(first.artifactPath, candidate.artifactPath)
                error("fixture load validation failed")
            }
            assertTrue(candidate.artifactPath.contains("staging"))
        }

        assertEquals(2, validationCalls)
        assertEquals("1.0.0", repaired.version)
        assertTrue(File(repaired.artifactPath).isFile)
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

    @Test
    fun `bundled ABI upgrade works offline and preserves disabled state`() = runTest {
        val artifact = createFixtureArtifact()
        val entry = fixtureEntry("1.0.0")
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val installer = createInstaller(repository, entry, artifact)
        val previous = installer.install(entry)
        repository.upsert(previous.copy(enabled = false, manifest = previous.manifest.copy(pluginApiVersion = 2)))
        val manifest = previous.manifest.copy(version = "1.0.1")
        val installed = installer.installBundled(manifest, { artifact }) { candidate ->
            val loaded = createSourcePluginLoader(object : Context() {}).load(
                Path(candidate.artifactPath).inSystem, candidate.manifest.entryClass, fixtureContext(),
            )
            loaded.close()
        }
        assertFalse(installed.enabled)
        assertEquals(3, repository.snapshot().plugins.single().manifest.pluginApiVersion)
        assertTrue(File(installed.artifactPath).isFile)
        assertFalse(File(previous.artifactPath).exists())
    }

    @Test
    fun `corrupt bundled upgrade retains the old installation for retry`() = runTest {
        val artifact = createFixtureArtifact()
        val entry = fixtureEntry("1.0.0")
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val installer = createInstaller(repository, entry, artifact)
        val previous = installer.install(entry)
        val old = previous.copy(manifest = previous.manifest.copy(pluginApiVersion = 2))
        repository.upsert(old)
        val manifest = previous.manifest.copy(version = "1.0.1")
        assertFailsWith<IllegalStateException> {
            installer.installBundled(manifest, { byteArrayOf(1, 2, 3) })
        }
        assertEquals(old, repository.snapshot().plugins.single())
        assertTrue(File(previous.artifactPath).readBytes().contentEquals(artifact))
        val installed = installer.installBundled(manifest, { artifact })
        assertEquals("1.0.1", installed.version)
    }

    @Test
    fun `bundled candidate load failure preserves the old ABI and files`() = runTest {
        val artifact = createFixtureArtifact()
        val entry = fixtureEntry("1.0.0")
        val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
        val installer = createInstaller(repository, entry, artifact)
        val previous = installer.install(entry)
        val old = previous.copy(manifest = previous.manifest.copy(pluginApiVersion = 2))
        repository.upsert(old)
        assertFailsWith<LinkageError> {
            installer.installBundled(previous.manifest.copy(version = "1.0.1"), { artifact }) {
                throw LinkageError("incompatible entry point")
            }
        }
        assertEquals(old, repository.snapshot().plugins.single())
        assertTrue(File(previous.artifactPath).readBytes().contentEquals(artifact))
        assertFalse(root.resolve("installed").resolve("fixture").resolve("1.0.1").exists())
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
          "pluginApiVersion":3,"minHostVersion":"1.0.0",
          "entryClass":"com.wynime.app.domain.sourceplugin.LoaderFixtureEntryPoint",
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
                "com/wynime/app/domain/sourceplugin/LoaderFixtureEntryPoint.class",
                "com/wynime/app/domain/sourceplugin/LoaderFixturePlugin.class",
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
