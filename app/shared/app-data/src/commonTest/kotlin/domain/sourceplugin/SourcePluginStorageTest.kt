/*
 * Copyright (C) 2026 OpenAni contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.domain.sourceplugin

import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePluginStorageTest {
    private val root = SystemPaths.createTempDirectory("source-plugin-storage-test")
    private val storage = SourcePluginStorage(root)

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `plugin and version are single safe path components`() {
        assertFailsWith<IllegalArgumentException> { storage.installedDirectory("../escape", "1.0.0") }
        assertFailsWith<IllegalArgumentException> { storage.installedDirectory("plugin", "../escape") }
        assertFailsWith<IllegalArgumentException> { storage.newStagingDirectory("plugin/name", "1.0.0") }
        assertFailsWith<IllegalArgumentException> { storage.newStagingDirectory("plugin", "") }
    }

    @Test
    fun `commit moves the complete staging directory into installed storage`() {
        val staging = storage.newStagingDirectory("demo", "1.0.0")
        val stagedArtifact = staging.resolve("package/plugin.jar")
        stagedArtifact.path.parent?.inSystem?.createDirectories()
        stagedArtifact.writeText("fixture")

        storage.ensureRootDirectories()
        val installed = storage.commit(staging, "demo", "1.0.0")

        assertFalse(staging.exists())
        assertEquals("fixture", installed.resolve("package/plugin.jar").readText())
        assertTrue(installed.exists())
    }

    @Test
    fun `delete installed is idempotent`() {
        storage.ensureRootDirectories()
        val installed = storage.installedDirectory("demo", "1.0.0").apply { createDirectories() }
        installed.resolve("package").createDirectories()
        installed.resolve("package/plugin.jar").writeText("fixture")
        val plugin = InstalledSourcePlugin(
            id = "demo",
            version = "1.0.0",
            manifest = SourcePluginManifest(
                id = "demo",
                displayName = "Demo",
                version = "1.0.0",
                pluginApiVersion = SOURCE_PLUGIN_API_VERSION,
                minHostVersion = "1.0.0",
                entryClass = "demo.Entry",
                website = "https://demo.example",
                platforms = emptySet(),
                artifacts = emptyMap(),
            ),
            artifactPath = installed.resolve("package/plugin.jar").absolutePath,
        )

        storage.deleteInstalled(plugin)
        storage.deleteInstalled(plugin)

        assertFalse(installed.exists())
    }
}
