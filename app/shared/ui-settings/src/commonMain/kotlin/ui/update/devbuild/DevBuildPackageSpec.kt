package com.wynime.app.ui.update.devbuild

import androidx.compose.runtime.Immutable
import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform

@Immutable
data class DevBuildPackageSpec(

    val artifactNames: List<String>,
    val kind: DevBuildPackageKind,

    val debugArtifactNames: List<String> = emptyList(),
) {
    init {
        require(artifactNames.isNotEmpty()) { "artifactNames must not be empty" }
    }

    val candidateArtifactNames: List<String> get() = artifactNames + debugArtifactNames

    fun isDebugArtifact(name: String): Boolean = name in debugArtifactNames

    fun packageFileName(shortSha: String): String = "wynime-main-$shortSha.${kind.packageExtension}"

    companion object {

        fun forPlatform(platform: Platform): DevBuildPackageSpec? = when (platform) {
            is Platform.Windows -> when (platform.arch) {
                Arch.X86_64 -> DevBuildPackageSpec(
                    listOf("wynime-windows-portable"),
                    DevBuildPackageKind.WINDOWS_PORTABLE_ZIP,
                )

                Arch.AARCH64 -> DevBuildPackageSpec(
                    listOf("wynime-windows-aarch64-portable"),
                    DevBuildPackageKind.WINDOWS_PORTABLE_ZIP,
                )

                Arch.ARMV7A, Arch.ARMV8A -> null
            }

            is Platform.Android -> {
                val abi = when (platform.arch) {
                    Arch.ARMV8A, Arch.AARCH64 -> "arm64-v8a"
                    Arch.ARMV7A -> "armeabi-v7a"
                    Arch.X86_64 -> "x86_64"
                }
                DevBuildPackageSpec(
                    listOf("wynime-android-$abi-release", "wynime-android-universal-release"),
                    DevBuildPackageKind.ANDROID_APK,
                    debugArtifactNames = listOf("wynime-android-$abi-debug", "wynime-android-universal-debug"),
                )
            }

            else -> null
        }
    }
}

enum class DevBuildPackageKind(

    val packageExtension: String,

    val supportsAutomaticInstall: Boolean,
) {

    WINDOWS_PORTABLE_ZIP(packageExtension = "zip", supportsAutomaticInstall = true),

    ANDROID_APK(packageExtension = "apk", supportsAutomaticInstall = true),

    ;

    val extractsFromArchive: Boolean get() = this != WINDOWS_PORTABLE_ZIP
}
