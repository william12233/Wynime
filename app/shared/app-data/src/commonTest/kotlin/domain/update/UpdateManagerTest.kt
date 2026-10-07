package com.wynime.app.domain.update

import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.exists
import com.wynime.utils.io.resolve
import com.wynime.utils.io.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManagerTest {
    private val rootDir: SystemPath = SystemPaths.createTempDirectory("update-manager-test")
    private val manager = UpdateManager(rootDir)

    @AfterTest
    fun cleanup() {
        rootDir.deleteRecursively()
    }

    private fun file(path: SystemPath): SystemPath = path.apply { writeText("x") }

    @Test
    fun `dev builds dir is a sibling of the auto update save dir`() {
        assertEquals(rootDir.resolve("download"), manager.saveDir)
        assertEquals(rootDir.resolve("dev-builds"), manager.devBuildsDir)
    }

    @Test
    fun `devBuildShaOf extracts the sha only from CI dev build version names`() {
        assertEquals("45f0e6fb", UpdateManager.devBuildShaOf("4.9.0-main-45f0e6fb"))
        assertNull(UpdateManager.devBuildShaOf("4.9.0-dev"))
        assertNull(UpdateManager.devBuildShaOf("6.2.0-beta01"))
        assertNull(UpdateManager.devBuildShaOf("6.1.0"))
    }

    @Test
    fun `deleteStaleInstallers keeps the files being downloaded and removes the rest`() {
        manager.saveDir.createDirectories()
        val keep = file(manager.saveDir.resolve("ani-6.2.0-beta01-macos-aarch64.dmg"))
        val keepSha1 = file(manager.saveDir.resolve("ani-6.2.0-beta01-macos-aarch64.dmg.sha1"))
        val stale = file(manager.saveDir.resolve("ani-6.1.0-macos-aarch64.dmg"))
        val staleSha1 = file(manager.saveDir.resolve("ani-6.1.0-macos-aarch64.dmg.sha1"))

        manager.deleteStaleInstallers(
            listOf("ani-6.2.0-beta01-macos-aarch64.dmg", "ani-6.2.0-beta01-macos-aarch64.dmg.sha1"),
        )

        assertTrue(keep.exists())
        assertTrue(keepSha1.exists())
        assertFalse(stale.exists())
        assertFalse(staleSha1.exists())
    }

    @Test
    fun `deleteStaleInstallers ignores directories instead of failing`() {

        val dir = manager.saveDir.resolve("dev-builds").apply { createDirectories() }
        file(dir.resolve("ani-main-45f0e6fb.dmg"))
        val stale = file(manager.saveDir.resolve("ani-6.1.0-windows-x86_64.zip"))

        manager.deleteStaleInstallers(listOf("ani-6.2.0-beta01-windows-x86_64.zip"))

        assertTrue(dir.exists(), "directories are not installers and must be left alone")
        assertFalse(stale.exists())
    }

    @Test
    fun `deleteStaleInstallers is a no-op when save dir does not exist`() {
        manager.deleteStaleInstallers(listOf("wynime.zip"))
        assertFalse(manager.saveDir.exists())
    }

    @Test
    fun `deleteInstalledFiles removes the auto update installer of the current version only`() {
        manager.saveDir.createDirectories()
        val installed = file(manager.saveDir.resolve("ani-6.2.0-beta01-macos-aarch64.dmg"))
        val other = file(manager.saveDir.resolve("ani-6.2.0-beta02-macos-aarch64.dmg"))

        manager.deleteInstalledFiles(currentVersion = "6.2.0-beta01")

        assertFalse(installed.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `deleteInstalledFiles removes dev builds dir after its package was installed`() {
        manager.devBuildsDir.createDirectories()
        file(manager.devBuildsDir.resolve("ani-main-45f0e6fb.dmg"))

        manager.deleteInstalledFiles(currentVersion = "4.9.0-main-45f0e6fb")

        assertFalse(manager.devBuildsDir.exists())
    }

    @Test
    fun `deleteInstalledFiles keeps dev builds dir when its package is for another commit`() {

        manager.devBuildsDir.createDirectories()
        val pending = file(manager.devBuildsDir.resolve("ani-main-0badcafe.AppImage"))

        manager.deleteInstalledFiles(currentVersion = "4.9.0-main-45f0e6fb")

        assertTrue(pending.exists())
    }

    @Test
    fun `deleteInstalledFiles keeps dev builds dir when current version is not a CI dev build`() {
        manager.devBuildsDir.createDirectories()
        val pending = file(manager.devBuildsDir.resolve("ani-main-45f0e6fb.dmg"))

        manager.deleteInstalledFiles(currentVersion = "6.2.0-beta01")

        assertTrue(pending.exists())
    }

    @Test
    fun `deleteInstalledFiles removes the legacy dev-builds dir nested inside save dir`() {
        val legacy = manager.saveDir.resolve("dev-builds").apply { createDirectories() }
        file(legacy.resolve("ani-main-0badcafe.zip"))
        val installer = file(manager.saveDir.resolve("ani-6.1.0-macos-aarch64.dmg"))

        manager.deleteInstalledFiles(currentVersion = "6.2.0-beta01")

        assertFalse(legacy.exists())
        assertTrue(installer.exists(), "only the legacy dir is removed")
        assertFalse(manager.devBuildsDir.exists(), "the new dev builds dir is not created as a side effect")
    }
}
