package com.wynime.app.ui.update.devbuild

import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform
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
    fun `package file name uses the short sha and the package extension`() {
        val spec = DevBuildPackageSpec.forPlatform(Platform.Windows(Arch.AARCH64))!!
        assertEquals("wynime-main-28ec14ac.zip", spec.packageFileName("28ec14ac"))
        assertEquals(
            "wynime-main-28ec14ac.apk",
            DevBuildPackageSpec.forPlatform(Platform.Android(Arch.ARMV8A))!!.packageFileName("28ec14ac"),
        )
    }
}
