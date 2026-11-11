package com.wynime.app.tools.update

import kotlinx.io.files.Path
import com.wynime.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateInstallerPackageTest {

    @Test
    fun `windows installs zip packages only`() {
        assertTrue(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.zip").inSystem))
        assertTrue(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.ZIP").inSystem))
        assertFalse(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.dmg").inSystem))
        assertFalse(WindowsUpdateInstaller.isInstallablePackage(Path("C:\\downloads\\ani.exe").inSystem))
    }

    @Test
    fun `windows updater is handed off through cmd`() {
        assertEquals(
            listOf(
                "cmd.exe",
                "/d",
                "/c",
                "start",
                "cmd.exe",
                "/c",
                "\"",
                "C:\\Wynime\\ani_update.exe",
                "C:\\Downloads\\wynime-update.zip",
                "C:\\Wynime",
                "\"",
            ),
            windowsUpdaterCommand(
                commandInterpreter = "cmd.exe",
                updaterExecutable = "C:\\Wynime\\ani_update.exe",
                packageFile = "C:\\Downloads\\wynime-update.zip",
                appDirectory = "C:\\Wynime",
            ),
        )
    }

}
