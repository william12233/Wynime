import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

plugins {
    id("wynime.base")
    `java-library`
}

val bindingSqliteVersion = "2.6.1"
val bindingCommit = "ce10e55447f4e0fd21a9001d0589a6e1e7a5a8d7"
val bindingSha256 = "4C535DFF9D2E30E8B0B5203455167020E102185E22F3056A27DA0B1AB71D3897"

val sqliteVersion = "3.50.1"
val sqliteAmalgamationVersion = "3500100"
val sqliteAmalgamationYear = "2025"
val sqliteAmalgamationSha256 = "41716B44AC8777188C4C3F1F370F01C9CB9E3B6428EB5C981D086C35DE2D9D3F"

val isWindowsArm64Host = getOs() == Os.Windows && getArch() == Arch.AARCH64

data class DownloadSource(val url: String, val base64Encoded: Boolean = false) : java.io.Serializable

abstract class DownloadAndVerify : DefaultTask() {
    @get:Input
    abstract val sources: ListProperty<DownloadSource>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val target: RegularFileProperty

    @TaskAction
    fun run() {
        val sources = sources.get()
        require(sources.isNotEmpty()) { "No download sources configured for ${target.get().asFile.name}" }
        var lastError: Exception? = null
        for (attempt in 1..5) {
            for (source in sources) {
                val bytes = try {
                    URI(source.url).toURL().openStream().use { it.readBytes() }
                } catch (e: Exception) {
                    lastError = e
                    logger.warn("Downloading ${source.url} failed on attempt $attempt/5: ${e.message}")
                    continue
                }
                val content = if (source.base64Encoded) {
                    Base64.getMimeDecoder().decode(String(bytes, Charsets.US_ASCII).trim())
                } else {
                    bytes
                }

                val actual = MessageDigest.getInstance("SHA-256").digest(content)
                    .joinToString("") { "%02X".format(it) }
                if (!actual.equals(sha256.get(), ignoreCase = true)) {
                    throw GradleException("SHA256 mismatch for ${source.url}. Expected ${sha256.get()}, got $actual.")
                }

                val targetFile = target.get().asFile
                targetFile.parentFile.mkdirs()
                targetFile.writeBytes(content)
                return
            }
            if (attempt < 5) {
                Thread.sleep(minOf(60_000L, 5_000L * (1L shl attempt)))
            }
        }
        throw GradleException(
            "Failed to download ${target.get().asFile.name} from any source: ${sources.map { it.url }}",
            lastError,
        )
    }
}

abstract class CompileSqliteJni @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val amalgamationDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bindingSource: RegularFileProperty

    @get:Input
    abstract val expectedSqliteVersion: Property<String>

    @get:Input
    abstract val javaHome: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val amalgamationDir = amalgamationDirectory.get().asFile
        val expected = expectedSqliteVersion.get()
        val header = amalgamationDir.resolve("sqlite3.h").readText()
        if (!Regex("""#define\s+SQLITE_VERSION\s+"${Regex.escape(expected)}"""").containsMatchIn(header)) {
            throw GradleException("Downloaded SQLite amalgamation does not match $expected.")
        }

        val vcvars = findVisualStudioWithArm64Tools().resolve("VC\\Auxiliary\\Build\\vcvarsall.bat")
        if (!vcvars.isFile) {
            throw GradleException("vcvarsall.bat was not found at $vcvars.")
        }

        val javaHomeDir = File(javaHome.get())
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()
        val objDir = temporaryDir
        val dll = outputDir.resolve("sqliteJni.dll")
        val sqliteObj = objDir.resolve("sqlite3.obj")
        val bindingObj = objDir.resolve("sqlite_bindings.obj")

        val defines = listOf(
            "HAVE_USLEEP=1",
            "SQLITE_DEFAULT_AUTOVACUUM=1",
            "SQLITE_DEFAULT_MEMSTATUS=0",
            "SQLITE_DEFAULT_WAL_SYNCHRONOUS=1",
            "SQLITE_ENABLE_COLUMN_METADATA",
            "SQLITE_ENABLE_FTS3",
            "SQLITE_ENABLE_FTS3_PARENTHESIS",
            "SQLITE_ENABLE_FTS4",
            "SQLITE_ENABLE_FTS5",
            "SQLITE_ENABLE_JSON1",
            "SQLITE_ENABLE_MATH_FUNCTIONS",
            "SQLITE_ENABLE_NORMALIZE",
            "SQLITE_ENABLE_RTREE",
            "SQLITE_ENABLE_STAT4",
            "SQLITE_HAVE_ISNAN",
            "SQLITE_OMIT_BUILTIN_TEST",
            "SQLITE_OMIT_DEPRECATED",
            "SQLITE_OMIT_PROGRESS_CALLBACK",
            "SQLITE_OMIT_SHARED_CACHE",
            "SQLITE_SECURE_DELETE",
            "SQLITE_TEMP_STORE=3",
            "SQLITE_THREADSAFE=2",
        ).joinToString(" ") { "/D$it" }

        val script = objDir.resolve("build-sqlite-jni.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "call \"${vcvars.absolutePath}\" arm64 || exit /b 1",
                "cl /nologo /O2 /Brepro /MT /utf-8 $defines " +
                        "/I\"${amalgamationDir.absolutePath}\" " +
                        "/Fo\"${sqliteObj.absolutePath}\" " +
                        "/c \"${amalgamationDir.resolve("sqlite3.c").absolutePath}\" || exit /b 1",
                "cl /nologo /O2 /Brepro /MT /EHsc /std:c++17 /utf-8 $defines " +
                        "/I\"${amalgamationDir.absolutePath}\" " +
                        "/I\"${javaHomeDir.resolve("include").absolutePath}\" " +
                        "/I\"${javaHomeDir.resolve("include\\win32").absolutePath}\" " +
                        "/Fo\"${bindingObj.absolutePath}\" " +
                        "/c \"${bindingSource.get().asFile.absolutePath}\" || exit /b 1",
                "link /nologo /Brepro /DLL /OUT:\"${dll.absolutePath}\" " +
                        "/IMPLIB:\"${objDir.resolve("sqliteJni.lib").absolutePath}\" " +
                        "\"${sqliteObj.absolutePath}\" \"${bindingObj.absolutePath}\" || exit /b 1",
            ).joinToString("\r\n", postfix = "\r\n"),
        )

        execOperations.exec {
            commandLine("cmd.exe", "/d", "/s", "/c", script.absolutePath)
        }
    }

    private fun findVisualStudioWithArm64Tools(): File {
        val vswhere = File(
            System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)",
            "Microsoft Visual Studio\\Installer\\vswhere.exe",
        )
        if (!vswhere.isFile) {
            throw GradleException(
                "vswhere.exe was not found at $vswhere. " +
                        "Install Visual Studio with the C++ ARM64 build tools.",
            )
        }
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(
                vswhere.absolutePath,
                "-latest", "-products", "*",
                "-requires", "Microsoft.VisualStudio.Component.VC.Tools.ARM64",
                "-property", "installationPath",
            )
            standardOutput = output
        }
        val path = output.toString(Charsets.UTF_8.name()).trim()
        if (path.isEmpty()) {
            throw GradleException(
                "Visual Studio ARM64 C++ tools were not found. " +
                        "Install the \"MSVC v143 - VS 2022 C++ ARM64 build tools\" component.",
            )
        }
        return File(path)
    }
}

val downloadSqliteAmalgamation = tasks.register<DownloadAndVerify>("downloadSqliteAmalgamation") {
    description = "Downloads the SQLite $sqliteVersion amalgamation source archive"
    sources = listOf(
        DownloadSource("https://www.sqlite.org/$sqliteAmalgamationYear/sqlite-amalgamation-$sqliteAmalgamationVersion.zip"),
    )
    sha256 = sqliteAmalgamationSha256
    target = layout.buildDirectory.file("sqlite-woa64/sqlite-amalgamation-$sqliteAmalgamationVersion.zip")
}

val unzipSqliteAmalgamation = tasks.register<Sync>("unzipSqliteAmalgamation") {
    description = "Unpacks the SQLite amalgamation source archive"
    from(zipTree(downloadSqliteAmalgamation.flatMap { it.target })) {

        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("sqlite-woa64/amalgamation"))
}

val downloadSqliteBinding = tasks.register<DownloadAndVerify>("downloadSqliteBinding") {
    description = "Downloads sqlite_bindings.cpp of androidx.sqlite $bindingSqliteVersion"
    val path = "sqlite/sqlite-bundled/src/jvmAndroidMain/jni/sqlite_bindings.cpp"

    sources = listOf(
        DownloadSource("https://raw.githubusercontent.com/androidx/androidx/$bindingCommit/$path"),
        DownloadSource(
            "https://android.googlesource.com/platform/frameworks/support/+/$bindingCommit/$path?format=TEXT",
            base64Encoded = true,
        ),
    )
    sha256 = bindingSha256
    target = layout.buildDirectory.file("sqlite-woa64/sqlite_bindings.cpp")
}

if (isWindowsArm64Host) {
    val compileSqliteJni = tasks.register<CompileSqliteJni>("compileSqliteJni") {
        description = "Builds natives/windows_arm64/sqliteJni.dll for androidx.sqlite $bindingSqliteVersion"
        amalgamationDirectory = layout.dir(unzipSqliteAmalgamation.map { it.destinationDir })
        bindingSource = downloadSqliteBinding.flatMap { it.target }
        expectedSqliteVersion = sqliteVersion
        javaHome = providers.environmentVariable("JAVA_HOME")
            .orElse(providers.systemProperty("java.home"))
        outputDirectory = layout.buildDirectory.dir("sqlite-woa64/native")
    }

    tasks.jar {
        from(compileSqliteJni.flatMap { it.outputDirectory.file("sqliteJni.dll") }) {
            into("natives/windows_arm64")
        }
    }
}
