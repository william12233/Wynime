package com.wynime.app.ui.update.devbuild

import kotlinx.coroutines.test.runTest
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.readBytes
import com.wynime.utils.io.resolve
import com.wynime.utils.io.toFile
import com.wynime.utils.io.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevBuildPackageExtractorTest {
    private inline fun withTempDir(block: (SystemPath) -> Unit) {
        val dir = SystemPaths.createTempDirectory("dev-build-extractor-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `extracts the first entry with the extension ignoring case and directories`() = runTest {
        withTempDir { dir ->
            val dmg = byteArrayOf(1, 2, 3)
            val archive = dir.resolve("artifact.zip")
            archive.writeBytes(
                zipBytes(
                    "readme.txt" to byteArrayOf(9),
                    "nested.DMG/" to byteArrayOf(),
                    "Wynime-4.12.0.DMG" to dmg,
                    "other.dmg" to byteArrayOf(4),
                ),
            )
            val target = dir.resolve("out.dmg")
            assertTrue(extractZipEntryByExtension(archive, "dmg", target))
            assertContentEquals(dmg, target.readBytes())
        }
    }

    @Test
    fun `returns false without creating the target when nothing matches`() = runTest {
        withTempDir { dir ->
            val archive = dir.resolve("artifact.zip")
            archive.writeBytes(zipBytes("readme.txt" to byteArrayOf(9)))
            val target = dir.resolve("out.apk")
            assertFalse(extractZipEntryByExtension(archive, "apk", target))
            assertFalse(target.exists())
        }
    }

    @Test
    fun `marks the file executable`() = runTest {
        withTempDir { dir ->
            val file = dir.resolve("wynime.AppImage")
            file.writeBytes(byteArrayOf(1))
            file.toFile().setExecutable(false, false)
            markExecutable(file)
            assertTrue(file.toFile().canExecute())
        }
    }
}
