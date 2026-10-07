import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
fun AbstractJPackageTask.unpackComposeDesktopNativeLibraries() {
    val triple = getOsTriple()
    val destinationDirFile = destinationDir.get().asFile

    fun isRuntimePayloadJar(file: File): Boolean {
        if (!file.isFile || file.extension != "jar") {
            return false
        }
        val name = file.name
        return name.startsWith("mediamp-mpv-runtime-") ||
                name.startsWith("mediamp-ffmpeg-runtime-")
    }

    destinationDirFile
        .walk()
        .filter(::isRuntimePayloadJar)
        .toList()
        .forEach { jar ->
            unpackJar(jar, jar.parentFile) {
                !(it.name.contains("MANIFEST") || it.name.contains("META-INF"))
            }
            jar.delete()

            logger.lifecycle(
                "Extracted ${jar.name} into ${jar.parentFile} and deleted the jars",
            )
        }

    val onnxruntimeJar = destinationDirFile.walk()
        .find {
            it.isFile &&
                    it.extension == "jar" &&
                    it.name.startsWith("onnxruntime-")
        }
        ?: throw FileNotFoundException(
            "onnxruntime library jar doesn't exist at app runtime directory after compose jpackage task.",
        )
    val appRuntimeDir = onnxruntimeJar.parentFile.toPath()

    val (archPathInJar, nativeLibraryNames) = when (triple) {
        "windows-x64" -> "win-x64" to listOf(
            "onnxruntime.dll",
            "onnxruntime4j_jni.dll",
        )

        else -> {
            logger.lifecycle("$triple is not supported for Wynime, ignoring unpack onnxruntime native library.")
            return
        }
    }

    val tempWorkDir = Files.createTempDirectory("wynime-build-onnxruntime")
    val tempRepackedJar = tempWorkDir.resolve(onnxruntimeJar.name)
    try {
        extractNativesAndRepackOnnxRuntimeJar(
            onnxruntimeJar = onnxruntimeJar,
            repackedJar = tempRepackedJar,
            tempNativeDir = tempWorkDir,
            archPathInJar = archPathInJar,
            nativeLibraryNames = nativeLibraryNames,
        )

        nativeLibraryNames.forEach { libraryName ->
            Files.move(
                tempWorkDir.resolve(libraryName),
                appRuntimeDir.resolve(libraryName),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        Files.move(
            tempRepackedJar,
            onnxruntimeJar.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        logger.lifecycle(
            "Extracted ${nativeLibraryNames.joinToString()} for $archPathInJar " +
                    "and replaced onnxruntime jar with all native libraries stripped.",
        )
    } finally {
        tempWorkDir.deleteRecursively()
    }

    val shaderProviderJar = destinationDirFile.walk()
        .find {
            it.isFile &&
                    it.extension == "jar" &&
                    it.name.startsWith("video-enhancement-shader-provider-desktop-")
        }
        ?: throw FileNotFoundException(
            "video enhancement shader provider jar doesn't exist at app runtime directory " +
                    "after compose jpackage task.",
        )
    val shaderAppRuntimeDir = shaderProviderJar.parentFile.toPath()
    val composeResourcesDir = shaderAppRuntimeDir.resolve("resources")
    val bundledShaderDir = composeResourcesDir.resolve(ANIME4K_SHADER_DIRECTORY)
    val shaderTempWorkDir = Files.createTempDirectory(shaderAppRuntimeDir, ".ani-build-anime4k-")
    val tempRepackedShaderProviderJar = shaderTempWorkDir.resolve(shaderProviderJar.name)
    val tempShaderDir = shaderTempWorkDir.resolve(ANIME4K_SHADER_DIRECTORY)
    try {
        val extractedShaderCount = extractShadersAndRepackProviderJar(
            shaderProviderJar = shaderProviderJar,
            repackedJar = tempRepackedShaderProviderJar,
            extractedShaderDir = tempShaderDir,
        )

        Files.createDirectories(composeResourcesDir)
        if (Files.exists(bundledShaderDir)) {
            bundledShaderDir.deleteRecursively()
        }
        Files.move(
            tempShaderDir,
            bundledShaderDir,
            StandardCopyOption.REPLACE_EXISTING,
        )
        Files.move(
            tempRepackedShaderProviderJar,
            shaderProviderJar.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        logger.lifecycle(
            "Extracted $extractedShaderCount video enhancement shader files into $bundledShaderDir " +
                    "and replaced ${shaderProviderJar.name} with all shader resources stripped.",
        )
    } finally {
        shaderTempWorkDir.deleteRecursively()
    }

}

private fun unpackJar(jar: File, dest: File, filter: (ZipEntry) -> Boolean = { true }) {
    val zip = ZipFile(jar)
    zip.use {
        zip.entries().asSequence().filter(filter).forEach { entry ->
            val file = dest.resolve(entry.name)
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

private const val ONNXRUNTIME_NATIVE_ROOT = "ai/onnxruntime/native/"

private const val VIDEO_ENHANCEMENT_SHADER_ROOT =
    "composeResources/com.wynime.utils.video.enhancement.shader.provider/files/shaders/"

private const val ANIME4K_SHADER_DIRECTORY = "anime4k"

private fun extractShadersAndRepackProviderJar(
    shaderProviderJar: File,
    repackedJar: Path,
    extractedShaderDir: Path,
): Int {
    var extractedShaderCount = 0
    Files.createDirectories(extractedShaderDir)

    ZipOutputStream(
        BufferedOutputStream(
            Files.newOutputStream(
                repackedJar,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ),
        ),
    ).use { zipOutput ->
        ZipFile(shaderProviderJar).use { sourceJar ->
            sourceJar.entries().asSequence().forEach { entry ->
                if (entry.name.startsWith(VIDEO_ENHANCEMENT_SHADER_ROOT)) {
                    if (!entry.isDirectory) {
                        val relativeName = entry.name.removePrefix(VIDEO_ENHANCEMENT_SHADER_ROOT)
                        require(relativeName.isNotEmpty()) {
                            "Shader resource has no file name: ${entry.name}"
                        }
                        val target = extractedShaderDir.resolve(relativeName).normalize()
                        require(target.startsWith(extractedShaderDir)) {
                            "Shader resource escapes the destination directory: ${entry.name}"
                        }
                        target.parent?.let(Files::createDirectories)
                        sourceJar.getInputStream(entry).use { input ->
                            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                        }
                        extractedShaderCount++
                    }
                    return@forEach
                }

                val repackedEntry = ZipEntry(entry.name).apply {
                    entry.comment?.let { comment = it }
                    entry.lastModifiedTime?.let { lastModifiedTime = it }
                }
                zipOutput.putNextEntry(repackedEntry)
                if (!entry.isDirectory) {
                    sourceJar.getInputStream(entry).use { input ->
                        input.copyTo(zipOutput)
                    }
                }
                zipOutput.closeEntry()
            }
        }
    }

    if (extractedShaderCount == 0) {
        throw FileNotFoundException(
            "Shader resources don't exist in ${shaderProviderJar.name}: $VIDEO_ENHANCEMENT_SHADER_ROOT",
        )
    }
    return extractedShaderCount
}

private fun extractNativesAndRepackOnnxRuntimeJar(
    onnxruntimeJar: File,
    repackedJar: Path,
    tempNativeDir: Path,
    archPathInJar: String,
    nativeLibraryNames: List<String>,
) {
    val requiredNativeEntries = nativeLibraryNames.associateBy { libraryName ->
        "$ONNXRUNTIME_NATIVE_ROOT$archPathInJar/$libraryName"
    }
    val missingNativeEntries = requiredNativeEntries.keys.toMutableSet()

    ZipOutputStream(
        BufferedOutputStream(
            Files.newOutputStream(
                repackedJar,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ),
        ),
    ).use { zipOutput ->
        ZipFile(onnxruntimeJar).use { sourceJar ->
            sourceJar.entries().asSequence().forEach { entry ->
                requiredNativeEntries[entry.name]?.let { libraryName ->
                    require(!entry.isDirectory) {
                        "Expected onnxruntime native library is a directory: ${entry.name}"
                    }
                    sourceJar.getInputStream(entry).use { input ->
                        Files.copy(
                            input,
                            tempNativeDir.resolve(libraryName),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    missingNativeEntries.remove(entry.name)
                }

                if (!entry.name.startsWith(ONNXRUNTIME_NATIVE_ROOT)) {
                    val repackedEntry = ZipEntry(entry.name).apply {
                        entry.comment?.let { comment = it }
                        entry.lastModifiedTime?.let { lastModifiedTime = it }
                    }
                    zipOutput.putNextEntry(repackedEntry)
                    if (!entry.isDirectory) {
                        sourceJar.getInputStream(entry).use { input ->
                            input.copyTo(zipOutput)
                        }
                    }
                    zipOutput.closeEntry()
                }
            }
        }
    }

    if (missingNativeEntries.isNotEmpty()) {
        throw FileNotFoundException(
            "onnxruntime native libraries don't exist in runtime jar: " +
                    missingNativeEntries.joinToString(),
        )
    }
}
