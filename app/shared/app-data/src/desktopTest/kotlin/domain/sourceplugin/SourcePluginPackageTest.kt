/*
 * Copyright (C) 2026 OpenAni contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.domain.sourceplugin

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePluginPackageTest {
    private val root = SystemPaths.createTempDirectory("source-plugin-package-test")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `jar package is copied to the destination`() {
        val archive = root.resolve("source.jar").apply { writeBytes(TEST_ARTIFACT) }
        val destination = root.resolve("jar-destination")

        val extracted = extractSourcePluginPackage(
            archive = archive,
            format = SourcePluginArtifactFormat.JAR,
            destination = destination,
        )

        assertEquals(destination.resolve("plugin.jar").absolutePath, extracted.absolutePath)
        assertEquals(TEST_ARTIFACT.toList(), File(extracted.absolutePath).readBytes().toList())
    }

    @Test
    fun `zip package extracts the loadable artifact`() {
        val archive = createZip(
            "metadata/readme.txt" to "fixture".encodeToByteArray(),
            "nested/plugin.jar" to TEST_ARTIFACT,
        )
        val destination = root.resolve("zip-destination")

        val extracted = extractSourcePluginPackage(
            archive = archive,
            format = SourcePluginArtifactFormat.ZIP,
            destination = destination,
        )

        assertEquals(destination.resolve("nested/plugin.jar").absolutePath, extracted.absolutePath)
        assertTrue(extracted.exists())
    }

    @Test
    fun `zip package rejects path traversal`() {
        val archive = createZip("../escaped.txt" to "outside".encodeToByteArray())
        val destination = root.resolve("unsafe-destination")

        assertFailsWith<IllegalArgumentException> {
            extractSourcePluginPackage(
                archive = archive,
                format = SourcePluginArtifactFormat.ZIP,
                destination = destination,
            )
        }

        assertFalse(root.resolve("escaped.txt").exists())
    }

    @Test
    fun `zip package requires a loadable artifact`() {
        val archive = createZip("metadata/readme.txt" to "fixture".encodeToByteArray())

        assertFailsWith<IllegalArgumentException> {
            extractSourcePluginPackage(
                archive = archive,
                format = SourcePluginArtifactFormat.ZIP,
                destination = root.resolve("missing-artifact"),
            )
        }
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): SystemPath {
        val archive = root.resolve("archive-${entries.size}-${entries.first().first.hashCode()}.zip")
        ZipOutputStream(FileOutputStream(File(archive.absolutePath))).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return archive
    }

    private companion object {
        val TEST_ARTIFACT = "plugin-fixture".encodeToByteArray()
    }
}
