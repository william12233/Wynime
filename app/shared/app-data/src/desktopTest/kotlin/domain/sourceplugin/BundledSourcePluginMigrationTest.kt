package com.wynime.app.domain.sourceplugin

import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.domain.foundation.DefaultHttpClientProvider
import com.wynime.app.domain.settings.NoProxyProvider
import com.wynime.app.platform.Context
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.resolve
import com.wynime.utils.io.writeBytes
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledSourcePluginMigrationTest {
    private val root = SystemPaths.createTempDirectory("bundled-plugin-migration")
    private val platform = SourcePluginPlatform.DESKTOP
    private val packages = ResourceSourcePluginPackages(platform)
    private val repository = InstalledSourcePluginRepository(MemoryDataStore(InstalledSourcePlugins.Empty))
    private val http = object : SourceHttpClient {
        override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse =
            error("Bundled migration must work offline")
    }

    @AfterTest
    fun cleanup() = root.deleteRecursively()

    private suspend fun oldInstallation(id: String, enabled: Boolean): InstalledSourcePlugin {
        val manifest = packages.manifest(id).copy(version = "1.0.25", pluginApiVersion = 2)
        val directory = root.resolve("installed").resolve(id).resolve("1.0.25")
        directory.createDirectories()
        val artifact = directory.resolve("old.jar")
        artifact.writeBytes(byteArrayOf(1, 2, 3))
        return InstalledSourcePlugin(id, manifest.version, manifest, artifact.absolutePath, enabled).also {
            repository.upsert(it)
        }
    }

    @Test
    fun `all eleven bundled sources upgrade offline and keep enabled choices`() = runTest {
        val previous = packages.pluginIds.mapIndexed { index, id -> oldInstallation(id, index % 2 == 0) }
        val provider = DefaultHttpClientProvider(NoProxyProvider, backgroundScope)
        val registry = SourcePluginRegistry(
            repository,
            SourcePluginInstaller(SourcePluginRepositoryClient(http), repository, SourcePluginStorage(root), platform, "0.1.3"),
            createSourcePluginLoader(object : Context() {}),
            SourcePluginContextFactory(provider, platform, "0.1.3"),
            packages,
        )
        try {
            registry.loadInstalled()
            assertEquals(11, registry.states.value.size)
            for (old in previous) {
                val current = repository.snapshot().plugins.single { it.id == old.id }
                assertEquals(packages.manifest(old.id).version, current.version)
                assertEquals(3, current.manifest.pluginApiVersion)
                assertEquals(old.enabled, current.enabled)
                assertTrue(File(current.artifactPath).isFile)
                assertTrue(File(old.artifactPath).isFile)
                val state = registry.states.value.single { it.installed.id == old.id }
                assertEquals(null, state.errorMessage)
                if (old.enabled) assertNotNull(state.metadata)
            }
        } finally {
            registry.close()
        }
    }

    @Test
    fun `fresh offline installs do not silently fall back to bundled sources`() = runTest {
        val registry = SourcePluginRegistry(
            repository,
            SourcePluginInstaller(SourcePluginRepositoryClient(http), repository, SourcePluginStorage(root), platform, "0.1.3"),
            createSourcePluginLoader(object : Context() {}),
            SourcePluginContextFactory(DefaultHttpClientProvider(NoProxyProvider, backgroundScope), platform, "0.1.3"),
            packages,
        )
        try {
            registry.loadInstalled()
            assertTrue(repository.snapshot().plugins.isEmpty())
            assertTrue(registry.states.value.isEmpty())
            val entries = registry.bundledEntries()
            assertEquals(packages.pluginIds, entries.map { it.id }.toSet())
            for (entry in entries) {
                assertEquals(packages.manifest(entry.id).version, entry.version)
            }
            kotlin.test.assertFailsWith<IllegalStateException> {
                registry.install(entries.first())
            }
            assertTrue(repository.snapshot().plugins.isEmpty())
        } finally {
            registry.close()
        }
    }

    @Test
    fun `unknown old ABI is reported incompatible before class loading`() = runTest {
        val known = oldInstallation(packages.pluginIds.first(), false)
        repository.remove(known.id)
        val unknown = known.copy(id = "third-party", manifest = known.manifest.copy(id = "third-party"))
        repository.upsert(unknown)
        var loadCalls = 0
        val loader = object : SourcePluginLoader {
            override fun load(artifact: SystemPath, entryClass: String, context: SourcePluginContext): LoadedSourcePlugin {
                loadCalls++
                error("Old ABI must never reach the loader")
            }
        }
        val registry = SourcePluginRegistry(
            repository,
            SourcePluginInstaller(SourcePluginRepositoryClient(http), repository, SourcePluginStorage(root), platform, "0.1.3"),
            loader,
            SourcePluginContextFactory(DefaultHttpClientProvider(NoProxyProvider, backgroundScope), platform),
            packages,
        )
        try {
            registry.loadInstalled()
            assertEquals(0, loadCalls)
            assertEquals(unknown, repository.snapshot().plugins.single())
            assertTrue(registry.states.value.single().errorMessage.orEmpty().contains("不相容"))
            assertTrue(File(unknown.artifactPath).isFile)
        } finally {
            registry.close()
        }
    }
}
