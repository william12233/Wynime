import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

object ReleaseArtifactNames {
    private const val appName = "wynime"

    const val officialAndroidArch = "arm64-v8a"
    const val officialWindowsOs = "windows"
    const val officialWindowsArch = "x86_64"

    fun fullVersionFromTag(tag: String): String = tag.removePrefix("v")

    fun androidApp(fullVersion: String, arch: String): String = "$appName-$fullVersion-$arch.apk"

    fun officialReleaseAssets(fullVersion: String): Set<String> = setOf(
        androidApp(fullVersion, officialAndroidArch),
        desktopDistributionFile(
            fullVersion = fullVersion,
            osName = officialWindowsOs,
            archName = officialWindowsArch,
            extension = "zip",
        ),
    )

    fun isOfficialReleaseTag(tag: String): Boolean = tag.startsWith("v")

    fun androidAppQr(fullVersion: String, arch: String, server: String): String =
        "${androidApp(fullVersion, arch)}.$server.qrcode.png"

    fun desktopDistributionFile(
        fullVersion: String,
        osName: String,
        archName: String = currentReleaseHostArch(),
        extension: String,
    ): String = "$appName-$fullVersion-$osName-$archName.$extension"

    fun server(fullVersion: String, extension: String): String = "$appName-server-$fullVersion.$extension"
}

enum class ReleaseHostOs {
    WINDOWS,
    MACOS,
    LINUX,
}

fun currentReleaseHostOs(): ReleaseHostOs =
    when (getOs()) {
        Os.Windows -> ReleaseHostOs.WINDOWS
        Os.MacOS -> ReleaseHostOs.MACOS
        Os.Linux -> ReleaseHostOs.LINUX
        Os.Unknown -> throw GradleException("Unsupported OS: ${System.getProperty("os.name")}")
    }

fun currentReleaseHostArch(): String =
    when (getArch()) {
        Arch.X86_64 -> "x86_64"
        Arch.AARCH64 -> "aarch64"
    }

abstract class ReleaseUploadTask : DefaultTask() {
    @get:Input
    abstract val releaseTag: Property<String>

    @get:Input
    abstract val releaseFullVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseId: Property<String>

    @get:Input
    @get:Optional
    abstract val githubRepository: Property<String>

    @get:Input
    @get:Optional
    abstract val githubToken: Property<String>

    @get:Input
    abstract val uploadToS3: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val awsAccessKeyId: Property<String>

    @get:Input
    @get:Optional
    abstract val awsSecretAccessKey: Property<String>

    @get:Input
    @get:Optional
    abstract val awsBaseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val awsRegion: Property<String>

    @get:Input
    @get:Optional
    abstract val awsBucket: Property<String>

    protected fun uploadReleaseAsset(
        name: String,
        contentType: String,
        file: File,
    ) {
        check(file.isFile) {
            "File '${file.absolutePath}' does not exist when attempting to upload '$name'."
        }

        val releaseId = requireConfigured(releaseId, "CI_RELEASE_ID")
        val repository = requireConfigured(githubRepository, "GITHUB_REPOSITORY")
        val token = requireConfigured(githubToken, "GITHUB_TOKEN")
        val tag = releaseTag.get()

        logger.lifecycle("tag = $tag")
        logger.lifecycle("fullVersion = ${releaseFullVersion.get()}")
        logger.lifecycle("releaseId = $releaseId")
        logger.lifecycle("repository = $repository")
        logger.lifecycle("token = ${token.isNotEmpty()}")

        val sha1File = File.createTempFile("release-asset-", ".sha1").apply {
            writeText(computeSha1Checksum(file))
        }
        val sha1FileName = "$name.sha1"

        try {
            runBlocking {
                HttpClient(OkHttp) {
                    expectSuccess = true
                }.use { client ->
                    uploadFileToGitHub(
                        client = client,
                        repository = repository,
                        releaseId = releaseId,
                        token = token,
                        file = file,
                        fileName = name,
                        contentType = ContentType.parse(contentType),
                    )
                    if (uploadToS3.get()) {
                        putS3Object(tag, name, file, contentType)
                        putS3Object(tag, sha1FileName, sha1File, "text/plain")
                    }
                }
            }
        } finally {
            sha1File.delete()
        }
    }

    private suspend fun uploadFileToGitHub(
        client: HttpClient,
        repository: String,
        releaseId: String,
        token: String,
        file: File,
        fileName: String,
        contentType: ContentType,
    ): Boolean {
        return try {
            client.post("https://uploads.github.com/repos/$repository/releases/$releaseId/assets") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                parameter("name", fileName)
                contentType(contentType)
                setBody(
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentType: ContentType
                            get() = contentType

                        override val contentLength: Long
                            get() = file.length()

                        override fun readFrom(): ByteReadChannel = file.readChannel()
                    },
                )
            }
            true
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 422) {
                logger.lifecycle("Asset already exists: $fileName")
                false
            } else {
                throw e
            }
        }
    }

    private fun putS3Object(
        tag: String,
        name: String,
        file: File,
        contentType: String,
    ) {
        val accessKeyId = requireConfigured(awsAccessKeyId, "AWS_ACCESS_KEY_ID")
        val secretAccessKey = requireConfigured(awsSecretAccessKey, "AWS_SECRET_ACCESS_KEY")
        val baseUrl = requireConfigured(awsBaseUrl, "AWS_BASEURL")
        val region = requireConfigured(awsRegion, "AWS_REGION")
        val bucket = requireConfigured(awsBucket, "AWS_BUCKET")

        S3Client.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey),
                ),
            )
            .endpointOverride(URI.create(baseUrl))
            .region(Region.of(region))
            .build()
            .use { client ->
                client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key("$tag/$name")
                        .contentType(contentType)
                        .build(),
                    RequestBody.fromFile(file),
                )
            }
    }

    private fun requireConfigured(property: Property<String>, name: String): String =
        property.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException(
                "Required property '$name' is missing. Configure it as a task input from the build script.",
            )

    private fun computeSha1Checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}

@DisableCachingByDefault(because = "Uploads release artifacts to external services")
abstract class UploadReleaseAssetTask : ReleaseUploadTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifactFile: RegularFileProperty

    @get:Input
    abstract val assetName: Property<String>

    @get:Input
    abstract val assetContentType: Property<String>

    @TaskAction
    fun upload() {
        uploadReleaseAsset(
            name = assetName.get(),
            contentType = assetContentType.get(),
            file = artifactFile.get().asFile,
        )
    }
}

@DisableCachingByDefault(because = "Uploads release artifacts to external services")
abstract class UploadAndroidApksTask : ReleaseUploadTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Input
    abstract val flavor: Property<String>

    @get:Input
    @get:Optional
    abstract val onlyArchitectures: SetProperty<String>

    @get:Inject
    protected abstract val execOperations: ExecOperations

    init {
        flavor.convention("default")
        onlyArchitectures.convention(emptySet())
    }

    @TaskAction
    fun uploadApks() {
        val fullVersion = releaseFullVersion.get()
        val flavorName = flavor.get()
        val requestedArchitectures = onlyArchitectures.get()
        val officialRelease = ReleaseArtifactNames.isOfficialReleaseTag(releaseTag.get())
        if (officialRelease) {
            require(flavorName == "default") {
                "Official releases publish the phone APK only; Android TV artifacts are not allowed."
            }
            require(requestedArchitectures == setOf(ReleaseArtifactNames.officialAndroidArch)) {
                "Official Android releases must select only ${ReleaseArtifactNames.officialAndroidArch}. " +
                    "Received: ${requestedArchitectures.joinToString()}"
            }
        }
        val apkFiles = apkDirectory.asFileTree.files
            .filter { it.isFile && it.extension == "apk" && it.name.contains("release") }
            .sortedBy { it.name }
            .mapNotNull { file ->
                val artifactName = file.name.removePrefix("android-$flavorName-")
                val releaseSuffix = when {
                    artifactName.endsWith("-release.apk") -> "-release.apk"
                    artifactName.endsWith("-release-unsigned.apk") -> throw GradleException(
                        "Refusing to publish unsigned Android release APK '${file.name}'. Configure the release signing key and rebuild.",
                    )
                    else -> throw GradleException(
                        "Cannot infer Android architecture from file name '${file.name}'",
                    )
                }
                val arch = artifactName.removeSuffix(releaseSuffix)
                if (requestedArchitectures.isEmpty() || arch in requestedArchitectures) {
                    file to arch
                } else {
                    null
                }
            }

        if (apkFiles.isEmpty()) {
            throw GradleException("No release APKs found in ${apkDirectory.get().asFile.absolutePath}")
        }

        if (officialRelease) {
            require(apkFiles.size == 1 && apkFiles.single().second == ReleaseArtifactNames.officialAndroidArch) {
                "Official Android releases must contain exactly one arm64-v8a release APK. " +
                    "Found: ${apkFiles.joinToString { it.second }}"
            }
        }

        apkFiles.forEach { (file, arch) ->
            if (officialRelease) {
                verifyFormalReleaseSigning(file)
            }
            uploadReleaseAsset(
                name = ReleaseArtifactNames.androidApp(fullVersion, arch),
                contentType = "application/vnd.android.package-archive",
                file = file,
            )
        }
    }

    private fun verifyFormalReleaseSigning(file: File) {
        val apksigner = findApkSigner()
        execOperations.exec {
            commandLine(
                apksigner.absolutePath,
                "verify",
                "--verbose",
                file.absolutePath,
            )
        }
        logger.lifecycle("Verified formal Android release signature: ${file.name}")
    }

    private fun findApkSigner(): File {
        val executableName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "apksigner.bat"
        } else {
            "apksigner"
        }

        val sdkRoots = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
        ).distinct()

        sdkRoots.forEach { sdkRootValue ->
            val buildToolsDirectory = File(sdkRootValue).resolve("build-tools")
            val versions = buildToolsDirectory.listFiles()
                ?.filter(File::isDirectory)
                ?.sortedByDescending(File::getName)
                .orEmpty()
            versions.forEach { versionDirectory ->
                val candidate = versionDirectory.resolve(executableName)
                if (candidate.isFile) return candidate
            }
        }

        throw GradleException(
            "Cannot verify the formal Android release signature: apksigner was not found " +
                "under ANDROID_HOME or ANDROID_SDK_ROOT.",
        )
    }
}

@DisableCachingByDefault(because = "Uploads release artifacts to external services")
abstract class UploadDesktopInstallersTask : ReleaseUploadTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val zipDistribution: RegularFileProperty

    @TaskAction
    fun uploadInstallers() {
        val fullVersion = releaseFullVersion.get()
        val officialRelease = ReleaseArtifactNames.isOfficialReleaseTag(releaseTag.get())

        if (officialRelease) {
            require(currentReleaseHostOs() == ReleaseHostOs.WINDOWS) {
                "Official releases publish the Windows x86_64 ZIP only."
            }
            require(currentReleaseHostArch() == ReleaseArtifactNames.officialWindowsArch) {
                "Official releases require a Windows x86_64 runner."
            }
        }

        require(currentReleaseHostOs() == ReleaseHostOs.WINDOWS) { "Windows distribution requires a Windows host" }
        uploadReleaseAsset(
                name = ReleaseArtifactNames.desktopDistributionFile(
                    fullVersion = fullVersion,
                    osName = ReleaseArtifactNames.officialWindowsOs,
                    archName = ReleaseArtifactNames.officialWindowsArch,
                    extension = "zip",
                ),
                contentType = "application/x-zip",
                file = requiredFile(zipDistribution, "zipDistribution"),
            )
    }

    private fun requiredFile(property: RegularFileProperty, propertyName: String): File =
        property.orNull?.asFile
            ?: throw GradleException("Required property '$propertyName' is not configured.")

    private fun requiredDirectory(property: DirectoryProperty, propertyName: String): File =
        property.orNull?.asFile
            ?: throw GradleException("Required property '$propertyName' is not configured.")

    private fun findSingleFile(directory: File, extension: String): File {
        if (!directory.isDirectory) {
            throw GradleException("Directory '${directory.absolutePath}' does not exist.")
        }
        return directory.walk()
            .singleOrNull { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?: throw GradleException("Expected exactly one '.$extension' file in ${directory.absolutePath}")
    }
}

