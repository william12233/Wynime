package com.wynime.app.ui.update

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.wynime.app.data.network.protocol.ReleaseClass
import com.wynime.app.platform.WynimeBrand
import com.wynime.app.tools.update.UpdatePackageDescriptor
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatform
import kotlin.coroutines.cancellation.CancellationException

class UpdateChecker(
    private val client: ScopedHttpClient,
    private val releasesUrl: String = WynimeBrand.githubReleasesApi,
    private val platformProvider: () -> Platform = ::currentPlatform,
) {

    suspend fun checkLatestVersion(
        releaseClass: ReleaseClass,
        currentVersion: String = currentWynimeBuildConfig.versionName,
    ): NewVersion? {
        val responseBody = client.use {
            val response = get(releasesUrl) {
                expectSuccess = false
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.UserAgent, "${WynimeBrand.name}/$currentVersion")
            }
            if (!response.status.isSuccess()) {
                return@use null
            }
            response.bodyAsText()
        } ?: return null

        val releases = try {
            json.decodeFromString<List<GitHubRelease>>(responseBody)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        }

        val current = ReleaseVersion.parse(currentVersion) ?: return null
        val platform = platformProvider()
        return releases.asSequence()
            .filterNot { it.draft }
            .mapNotNull { release ->
                val version = ReleaseVersion.parse(release.tagName) ?: return@mapNotNull null
                val candidateClass = version.releaseClass
                if (release.prerelease && candidateClass == ReleaseClass.STABLE) return@mapNotNull null
                if (!candidateClass.moreStableThan(releaseClass)) return@mapNotNull null
                if (version <= current) return@mapNotNull null

                val assetNames = assetNamesFor(version.displayName, platform)
                val assets = assetNames.mapNotNull { expected ->
                    release.assets.firstOrNull { it.name == expected }
                        ?.let { asset ->
                            asset.browserDownloadUrl.takeIf(String::isNotBlank)?.let { url -> asset.name to url }
                        }
                }
                if (assets.isEmpty()) return@mapNotNull null
                val (assetName, assetUrl) = assets.first()
                val abi = assetName
                    .removePrefix("wynime-${version.displayName}-")
                    .removeSuffix(".apk")
                    .takeIf { assetName.endsWith(".apk") }

                NewVersion(
                    name = version.displayName,
                    changelogs = listOf(
                        Changelog(
                            version = version.displayName,
                            publishedAt = release.publishedAt.orEmpty(),
                            changes = release.body.orEmpty(),
                        ),
                    ),
                    downloadUrlAlternatives = assets.map { it.second },
                    publishedAt = release.publishedAt.orEmpty(),
                    packageDescriptor = UpdatePackageDescriptor(
                        version = version.displayName,
                        filename = assetName,
                        downloadUrl = assetUrl,
                        abi = abi,
                    ),
                ) to version
            }
            .maxWithOrNull(compareBy<Pair<NewVersion, ReleaseVersion>> { it.second }.thenBy { it.first.publishedAt })
            ?.first
    }

    private fun assetNamesFor(version: String, platform: Platform): List<String> = when (platform) {
        is Platform.Windows -> when (platform.arch) {
            Arch.X86_64 -> listOf("wynime-$version-windows-x86_64.zip")
            Arch.AARCH64 -> listOf("wynime-$version-windows-aarch64.zip")
            Arch.ARMV7A, Arch.ARMV8A -> emptyList()
        }

        is Platform.Android -> when (platform.arch) {
            Arch.ARMV8A, Arch.AARCH64 -> listOf("wynime-$version-arm64-v8a.apk")
            Arch.ARMV7A -> listOf("wynime-$version-armeabi-v7a.apk")
            Arch.X86_64 -> listOf("wynime-$version-x86_64.apk")
        }

        else -> emptyList()
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
    )

    private data class ReleaseVersion(
        val displayName: String,
        private val numbers: List<Int>,
        private val qualifierRank: Int,
        private val qualifierNumber: Int,
        val releaseClass: ReleaseClass,
    ) : Comparable<ReleaseVersion> {
        override fun compareTo(other: ReleaseVersion): Int {
            for (index in 0 until maxOf(numbers.size, other.numbers.size)) {
                val comparison = (numbers.getOrElse(index) { 0 }).compareTo(other.numbers.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }
            return compareValuesBy(this, other, ReleaseVersion::qualifierRank, ReleaseVersion::qualifierNumber)
        }

        companion object {
            private val pattern = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?$")

            fun parse(raw: String): ReleaseVersion? {
                val normalized = raw.trim().removePrefix("v").removePrefix("V")
                val match = pattern.matchEntire(raw.trim()) ?: return null
                val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
                val qualifier = match.groupValues[2].lowercase().ifBlank { null }
                val (rank, releaseClass) = when {
                    qualifier == null -> 3 to ReleaseClass.STABLE
                    qualifier.startsWith("alpha") || qualifier.startsWith("dev") -> 0 to ReleaseClass.ALPHA
                    qualifier.startsWith("beta") -> 1 to ReleaseClass.BETA
                    qualifier.startsWith("rc") -> 2 to ReleaseClass.RC
                    else -> 0 to ReleaseClass.ALPHA
                }
                val qualifierNumber = qualifier
                    ?.dropWhile { !it.isDigit() }
                    ?.takeWhile(Char::isDigit)
                    ?.toIntOrNull()
                    ?: 0
                return ReleaseVersion(
                    displayName = normalized,
                    numbers = numbers,
                    qualifierRank = rank,
                    qualifierNumber = qualifierNumber,
                    releaseClass = releaseClass,
                )
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
