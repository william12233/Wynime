import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

val desktopApiClasses = layout.buildDirectory.dir("classes/kotlin/desktop/main")
val publicAbiSnapshot = layout.projectDirectory.file("abi/public-api-v3.txt").asFile
val apiVersionSource = rootProject.file(
    "app/shared/app-data/src/commonMain/kotlin/domain/sourceplugin/SourcePluginRepositoryModels.kt",
)

fun sourcePluginApiVersion(): Int = Regex("SOURCE_PLUGIN_API_VERSION\\s*=\\s*(\\d+)")
    .find(apiVersionSource.readText())
    ?.groupValues
    ?.get(1)
    ?.toInt()
    ?: error("SOURCE_PLUGIN_API_VERSION was not found in ${apiVersionSource.absolutePath}")

fun javapOutput(classpath: File, className: String): String {
    val javap = sequenceOf(
        File(System.getProperty("java.home"), "bin/javap.exe"),
        File(System.getProperty("java.home"), "bin/javap"),
    ).firstOrNull(File::isFile) ?: error("javap was not found under java.home")
    val builder = ProcessBuilder(
        javap.absolutePath,
        "-public",
        "-s",
        "-classpath",
        classpath.absolutePath,
        className,
    ).redirectErrorStream(true)
    builder.environment().remove("JAVA_TOOL_OPTIONS")
    val process = builder.start()
    val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        .replace("\r\n", "\n")
        .trim()
    check(process.waitFor(30, TimeUnit.SECONDS)) { "javap timed out for $className" }
    check(process.exitValue() == 0) { "javap failed for $className:\n$output" }
    return output
}

fun publicAbiSnapshotText(): String {
    val classpath = desktopApiClasses.get().asFile
    val classNames = classpath.walkTopDown()
        .filter { it.isFile && it.extension == "class" && '$' !in it.name }
        .map { it.relativeTo(classpath).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.') }
        .sorted()
        .toList()
    require(classNames.isNotEmpty()) { "No compiled plugin API classes found in $classpath" }
    return buildString {
        appendLine("pluginApiVersion=${sourcePluginApiVersion()}")
        appendLine("format=javap-public-signatures-v1")
        appendLine()
        classNames.forEach { className ->
            appendLine("[$className]")
            appendLine(javapOutput(classpath, className))
            appendLine()
        }
    }.trimEnd() + "\n"
}

val writePublicAbiSnapshot = tasks.register("writePublicAbiSnapshot") {
    notCompatibleWithConfigurationCache("The ABI snapshot invokes javap during task execution.")
    dependsOn(tasks.named("compileKotlinDesktop"))
    outputs.file(publicAbiSnapshot)
    doLast {
        publicAbiSnapshot.parentFile.mkdirs()
        publicAbiSnapshot.writeText(publicAbiSnapshotText(), StandardCharsets.UTF_8)
        logger.lifecycle("Wrote public plugin API ABI snapshot: ${publicAbiSnapshot.absolutePath}")
    }
}

val verifyPublicAbiSnapshot = tasks.register("verifyPublicAbiSnapshot") {
    dependsOn(tasks.named("compileKotlinDesktop"))
    inputs.file(publicAbiSnapshot)
    notCompatibleWithConfigurationCache(
        "The ABI verifier invokes javap against the compiled class directory during task execution.",
    )
    doLast {
        check(publicAbiSnapshot.isFile) {
            "Missing public plugin API ABI snapshot: ${publicAbiSnapshot.absolutePath}. " +
                "Run :source:plugin-api:writePublicAbiSnapshot and review the result."
        }
        val expected = publicAbiSnapshot.readText(StandardCharsets.UTF_8)
        val actual = publicAbiSnapshotText()
        if (expected != actual) {
            val snapshotVersion = Regex("^pluginApiVersion=(\\d+)", RegexOption.MULTILINE)
                .find(expected)?.groupValues?.get(1)?.toIntOrNull()
            val currentVersion = sourcePluginApiVersion()
            val action = if (snapshotVersion == currentVersion) {
                "bump SOURCE_PLUGIN_API_VERSION before accepting a breaking public ABI change"
            } else {
                "regenerate and review public-api-v${currentVersion}.txt"
            }
            error("Public plugin API ABI snapshot mismatch; $action")
        }
        logger.lifecycle("Public plugin API ABI snapshot is current (v${sourcePluginApiVersion()})")
    }
}

tasks.named("check") {
    dependsOn(verifyPublicAbiSnapshot)
}

kotlin {
    android {
        namespace = "com.wynime.source.plugin.api"
    }

    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.core)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
}
