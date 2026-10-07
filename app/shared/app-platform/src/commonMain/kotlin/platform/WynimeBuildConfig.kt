package com.wynime.app.platform

import androidx.compose.runtime.Stable
import com.wynime.utils.platform.currentPlatform

@Stable
interface WynimeBuildConfig {

    val versionName: String
    val isDebug: Boolean
    val sentryDsn: String

    val distroChannel: String

    val gitBranch: String
        get() = ""

    val gitCommitSha: String
        get() = ""

    val gitCommitTime: String
        get() = ""

    val sentryEnabled: Boolean
        get() = true
    val analyticsEnabled: Boolean
        get() = true

    val isDefaultDistro: Boolean
        get() = distroChannel == DISTRO_PLATFORM_DEFAULT

    companion object {
        @Stable
        fun current(): WynimeBuildConfig = currentWynimeBuildConfig

        private const val DISTRO_PLATFORM_DEFAULT = "default"
    }
}

val WynimeBuildConfig.fourDigitVersionCode: String
    get() = buildString {
        val split = versionName.substringBefore("-").split(".")
        if (split.size == 3) {
            split[0].toIntOrNull()?.let {
                append(it.toString())
            }
            split[1].toIntOrNull()?.let {
                append(it.toString().padStart(2, '0'))
            }
            split[2].toIntOrNull()?.let {
                append(it.toString())
            }
        } else {
            for (section in split) {
                section.toIntOrNull()?.let {
                    append(it.toString())
                }
            }
        }
    }

@Stable
@PublishedApi
internal expect val currentWynimeBuildConfigImpl: WynimeBuildConfig

@Stable
inline val currentWynimeBuildConfig: WynimeBuildConfig get() = currentWynimeBuildConfigImpl

fun getWynimeUserAgent(
    version: String = currentWynimeBuildConfig.versionName,
    platform: String = currentPlatform().nameAndArch,
): String = "Wynime/$version ($platform) (${WynimeBrand.githubHome})"
