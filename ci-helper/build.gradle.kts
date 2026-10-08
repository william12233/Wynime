@file:Suppress("UnstableApiUsage")

import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.Zip
import java.io.File

plugins {
    id("wynime.jvm-library")
    alias(libs.plugins.kotlinx.atomicfu)
}

fun ciProperty(name: String): Provider<String> {
    return providers.provider { getPropertyOrNull(name) }
}

val ciTag = ciProperty("CI_TAG").orElse("4.9.0-dev")
val ciReleaseFullVersion = ciTag.map(ReleaseArtifactNames::fullVersionFromTag)
val ciReleaseId = ciProperty("CI_RELEASE_ID")
val ciGithubRepository = ciProperty("GITHUB_REPOSITORY")
val ciGithubToken = ciProperty("GITHUB_TOKEN")
val ciUploadToS3 = ciProperty("UPLOAD_TO_S3").map { it.equals("true", ignoreCase = true) }.orElse(false)
val ciAwsAccessKeyId = ciProperty("AWS_ACCESS_KEY_ID")
val ciAwsSecretAccessKey = ciProperty("AWS_SECRET_ACCESS_KEY")
val ciAwsBaseUrl = ciProperty("AWS_BASEURL")
val ciAwsRegion = ciProperty("AWS_REGION")
val ciAwsBucket = ciProperty("AWS_BUCKET")
val ciAndroidAbis = ciProperty("wynime.android.abis")
    .map { value ->
        value.split(',')
            .map(String::trim)
            .filter { it.isNotEmpty() && it != "all" }
            .toSet()
    }
    .orElse(emptySet())
val githubRef = ciProperty("GITHUB_REF")
val githubSha = ciProperty("GITHUB_SHA")

fun ReleaseUploadTask.configureReleaseUploadInputs() {
    releaseTag.convention(ciTag)
    releaseFullVersion.convention(ciReleaseFullVersion)
    releaseId.convention(ciReleaseId)
    githubRepository.convention(ciGithubRepository)
    githubToken.convention(ciGithubToken)
    uploadToS3.convention(ciUploadToS3)
    awsAccessKeyId.convention(ciAwsAccessKeyId)
    awsSecretAccessKey.convention(ciAwsSecretAccessKey)
    awsBaseUrl.convention(ciAwsBaseUrl)
    awsRegion.convention(ciAwsRegion)
    awsBucket.convention(ciAwsBucket)
}

tasks.register("uploadAndroidApk", UploadAndroidApksTask::class) {
    configureReleaseUploadInputs()
    onlyArchitectures.set(ciAndroidAbis)
    apkDirectory.set(project(":app:android").layout.buildDirectory.dir("outputs/apk/default/release"))
}

tasks.register("uploadAndroidTvApk", UploadAndroidApksTask::class) {
    configureReleaseUploadInputs()
    apkDirectory.set(project(":app:android").layout.buildDirectory.dir("outputs/apk/tv/release"))
    flavor.set("tv")
}

val uploadAndroidApkGithubQr = tasks.register("uploadAndroidApkGithubQr", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(rootProject.layout.projectDirectory.file("apk-qrcode-github.png"))
    assetContentType.set("image/png")
    assetName.set(
        ciReleaseFullVersion.map { version ->
            ReleaseArtifactNames.androidAppQr(version, "universal", "github")
        },
    )
}

val uploadAndroidApkCloudflareQr = tasks.register("uploadAndroidApkCloudflareQr", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(rootProject.layout.projectDirectory.file("apk-qrcode-cloudflare.png"))
    assetContentType.set("image/png")
    assetName.set(
        ciReleaseFullVersion.map { version ->
            ReleaseArtifactNames.androidAppQr(version, "universal", "cloudflare")
        },
    )
}

tasks.register("uploadAndroidApkQR") {
    dependsOn(uploadAndroidApkGithubQr, uploadAndroidApkCloudflareQr)
}

val zipDesktopDistribution = tasks.register("zipDesktopDistribution", Zip::class) {
    dependsOn(
        ":app:desktop:createReleaseDistributable",
        ":app:desktop:copyReleaseLicenseNotices",
    )
    from(project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main-release/app"))

    useFileSystemPermissions()
    archiveBaseName.set("wynime")
    archiveVersion.set(ciReleaseFullVersion)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveExtension.set("zip")
}

tasks.register("uploadDesktopInstallers", UploadDesktopInstallersTask::class) {
    configureReleaseUploadInputs()
    dependsOn(zipDesktopDistribution)
    zipDistribution.set(zipDesktopDistribution.flatMap { it.archiveFile })
}

tasks.register("prepareArtifactsForManualUpload") {
    dependsOn(
        ":app:desktop:createReleaseDistributable",
        ":app:desktop:packageReleaseDistributionForCurrentOS",
        zipDesktopDistribution,
    )

    doLast {
        val distributionDir = layout.buildDirectory.dir("distribution").get().asFile.apply { mkdirs() }
        val releaseVersion = project.version.toString()

        fun copyToDistribution(name: String, file: File) {
            val target = distributionDir.resolve(name)
            target.delete()
            file.copyTo(target)
            println("File written: ${target.absoluteFile}")
        }

        copyToDistribution(
            name = ReleaseArtifactNames.desktopDistributionFile(
                releaseVersion,
                osName = currentReleaseHostOs().name.lowercase(),
                extension = "zip",
            ),
            file = zipDesktopDistribution.get().archiveFile.get().asFile,
        )
    }
}

val gradleProperties = rootProject.file("gradle.properties")

tasks.register("updateDevVersionNameFromGit") {
    doLast {
        val ref = githubRef.orNull ?: throw GradleException("GITHUB_REF is not provided.")
        val sha = githubSha.orNull ?: throw GradleException("GITHUB_SHA is not provided.")
        val propertiesText = gradleProperties.readText()
        val baseVersion = (
            Regex("version.name=(.+)").find(propertiesText)
                ?: error("Failed to find base version. Check version.name in gradle.properties")
            )
            .groupValues[1]
            .substringBefore("-")
        val branch = ref.substringAfterLast("/")
        val newVersion = "$baseVersion-$branch-${sha.take(8)}"
        println("New version name: $newVersion")
        gradleProperties.writeText(
            propertiesText.replaceFirst(Regex("version.name=(.+)"), "version.name=$newVersion"),
        )
    }
}

tasks.register("updateReleaseVersionNameFromGit") {
    doLast {
        val releaseVersion = ReleaseArtifactNames.fullVersionFromTag(ciTag.get())
        val packageVersion = releaseVersion.substringBefore("-")
        val propertiesText = gradleProperties.readText()
        println("New version: $releaseVersion, packageVersion=$packageVersion")
        gradleProperties.writeText(
            propertiesText
                .replaceFirst(Regex("version.name=(.+)"), "version.name=$releaseVersion")
                .replaceFirst(Regex("package.version=(.+)"), "package.version=$packageVersion"),

        )
    }
}
