package com.wynime.utils.platform

internal actual fun currentPlatformImpl(): Platform {
    val os = System.getProperty("os.name")?.lowercase() ?: error("Cannot determine platform, 'os.name' is null.")
    val arch = getArch()
    return when {
        "windows" in os -> Platform.Windows(arch)

        else -> throw UnsupportedOperationException("Unsupported platform: $os")
    }
}

private fun getArch() = System.getProperty("os.arch").lowercase().let {
    when {
        "x86" in it || "x64" in it || "amd64" in it -> Arch.X86_64
        "arm" in it || "aarch" in it -> Arch.AARCH64
        else -> Arch.X86_64
    }
}

fun currentPlatformDesktop(): Platform.Desktop {
    val platform = currentPlatform()
    check(platform is Platform.Desktop)
    return platform
}
