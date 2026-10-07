@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")

package com.wynime.utils.platform

import kotlin.contracts.contract

sealed class Platform {
    abstract val name: String
    abstract val arch: Arch

    val nameAndArch get() = "$name ${arch.displayName}"
    final override fun toString(): String = nameAndArch

    sealed class Mobile : Platform()

    data class Android(
        override val arch: Arch,
    ) : Mobile() {
        override val name: String get() = "Android"
    }

    sealed class Desktop(
        override val name: String
    ) : Platform()

    data class Windows(
        override val arch: Arch
    ) : Desktop("Windows")

}

@Suppress("ObjectPropertyName")
private val _currentPlatform = runCatching { currentPlatformImpl() }

fun currentPlatform(): Platform = _currentPlatform.getOrThrow()

enum class ArchFamily {
    X86,
    AARCH,
}

enum class Arch(
    val displayName: String,
    val family: ArchFamily,
    val addressSizeBits: Int,
) {

    X86_64("x86_64", ArchFamily.X86, 64),

    AARCH64("aarch64", ArchFamily.AARCH, 64),

    ARMV7A("armeabi-v7a", ArchFamily.AARCH, 32),

    ARMV8A("arm64-v8a", ArchFamily.AARCH, 64),
}

internal expect fun currentPlatformImpl(): Platform

inline fun Platform.isAArch(): Boolean = this.arch.family == ArchFamily.AARCH

inline fun Platform.is64bit(): Boolean = this.arch.addressSizeBits == 64

inline fun Platform.isDesktop(): Boolean {
    contract { returns(true) implies (this@isDesktop is Platform.Desktop) }
    return this is Platform.Desktop
}

inline fun Platform.isWindows(): Boolean {
    contract { returns(true) implies (this@isWindows is Platform.Windows) }
    return this is Platform.Windows
}

inline fun Platform.isMobile(): Boolean {
    contract { returns(true) implies (this@isMobile is Platform.Mobile) }
    return this is Platform.Mobile
}

inline fun Platform.isAndroid(): Boolean {
    contract { returns(true) implies (this@isAndroid is Platform.Android) }
    return this is Platform.Android
}

inline fun Platform.hasScrollingBug() = isDesktop()
