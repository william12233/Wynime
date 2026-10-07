package com.wynime.app.domain.sourceplugin

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.source.plugin.api.SourcePluginPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InstalledSourcePluginRepositoryTest {
    @Test
    fun `upsert replaces by id and preserves other plugins`() = runTest {
        val repository = InstalledSourcePluginRepository(
            MemoryDataStore(
                InstalledSourcePlugins(
                    plugins = listOf(plugin("one"), plugin("two")),
                ),
            ),
        )

        repository.upsert(plugin("one", version = "2.0.0"))

        assertEquals(
            listOf("two", "one"),
            repository.flow.first().plugins.map { it.id },
        )
        assertEquals("2.0.0", repository.flow.first().plugins.last().version)
    }

    @Test
    fun `enable and remove return the affected plugin`() = runTest {
        val repository = InstalledSourcePluginRepository(
            MemoryDataStore(InstalledSourcePlugins(listOf(plugin("one")))),
        )

        val disabled = repository.setEnabled("one", false)
        val removed = repository.remove("one")

        assertEquals(false, disabled?.enabled)
        assertFalse(repository.flow.first().plugins.any { it.id == "one" })
        assertEquals("one", removed?.id)
        assertNull(repository.setEnabled("missing", true))
        assertNull(repository.remove("missing"))
    }

    private fun plugin(id: String, version: String = "1.0.0") = InstalledSourcePlugin(
        id = id,
        version = version,
        manifest = SourcePluginManifest(
            id = id,
            displayName = id,
            version = version,
            pluginApiVersion = SOURCE_PLUGIN_API_VERSION,
            minHostVersion = "1.0.0",
            entryClass = "${id}.Entry",
            website = "https://$id.example",
            platforms = setOf(SourcePluginPlatform.DESKTOP),
            artifacts = emptyMap(),
        ),
        artifactPath = "/plugins/$id.jar",
    )
}
