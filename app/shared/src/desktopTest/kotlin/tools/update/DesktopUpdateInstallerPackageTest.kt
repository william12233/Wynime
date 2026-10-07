package com.wynime.app.tools.update

import kotlinx.io.files.Path
import com.wynime.utils.io.inSystem
import kotlin.test.Test
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

}
