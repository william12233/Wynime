/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevBuildPackageSpecTest {
    @Test
    fun `windows uses the portable zip artifact of its arch`() {
        assertEquals(
            DevBuildPackageSpec(listOf("wynime-windows-portable"), DevBuildPackageKind.WINDOWS_PORTABLE_ZIP),
            DevBuildPackageSpec.forPlatform(Platform.Windows(Arch.X86_64)),
        )
        assertEquals(
            DevBuildPackageSpec(listOf("wynime-windows-aarch64-portable"), DevBuildPackageKind.WINDOWS_PORTABLE_ZIP),
            DevBuildPackageSpec.forPlatform(Platform.Windows(Arch.AARCH64)),
        )
    }

    @Test
    fun `macos aarch64 uses the dmg artifact and x64 is unsupported`() {
        assertEquals(
            DevBuildPackageSpec(listOf("wynime-macos-dmg-aarch64"), DevBuildPackageKind.MACOS_DMG),
            DevBuildPackageSpec.forPlatform(Platform.MacOS(Arch.AARCH64)),
        )
        assertNull(DevBuildPackageSpec.forPlatform(Platform.MacOS(Arch.X86_64)))
    }

    @Test
    fun `linux x64 downloads the appimage for manual install`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.Linux(Arch.X86_64))
        assertEquals(DevBuildPackageSpec(listOf("wynime-linux-appimage-x64"), DevBuildPackageKind.LINUX_APPIMAGE), spec)
        assertEquals(false, spec!!.kind.supportsAutomaticInstall)
        assertNull(DevBuildPackageSpec.forPlatform(Platform.Linux(Arch.AARCH64)))
    }

    @Test
    fun `android prefers the abi specific release apk and falls back to universal`() {
        assertEquals(
            listOf("wynime-android-arm64-v8a-release", "wynime-android-universal-release"),
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.ARMV8A))!!.artifactNames,
        )
        assertEquals(
            listOf("wynime-android-armeabi-v7a-release", "wynime-android-universal-release"),
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.ARMV7A))!!.artifactNames,
        )
        assertEquals(
            listOf("wynime-android-x86_64-release", "wynime-android-universal-release"),
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.X86_64))!!.artifactNames,
        )
        assertEquals(
            DevBuildPackageKind.ANDROID_APK,
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.ARMV8A))!!.kind,
        )
    }

    @Test
    fun `ios is unsupported`() {
        assertNull(DevBuildPackageSpec.forPlatform(Platform.Ios))
    }

    @Test
    fun `package file name uses the short sha and the package extension`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.MacOS(Arch.AARCH64))!!
        assertEquals("wynime-main-28ec14ac.dmg", spec.packageFileName("28ec14ac"))
        assertEquals(
            "wynime-main-28ec14ac.AppImage",
            DevBuildPackageSpec.forPlatform(Platform.Linux(Arch.X86_64))!!.packageFileName("28ec14ac"),
        )
    }
}
