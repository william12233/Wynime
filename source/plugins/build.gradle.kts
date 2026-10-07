import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "tw.wynime.sources"
version = "1.0.25"

dependencies {
    compileOnly(project(":source:plugin-api"))
    testImplementation(kotlin("test"))
    testImplementation(project(":source:plugin-api"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.serialization.core)
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.test {
    useJUnitPlatform()
    exclude("**/SourcePluginLiveSmokeTest.class")
}

/**
 * Runs the real executable plugins against their current websites. External sites are not a
 * deterministic build input, so the process is deliberately non-blocking for release checks.
 */
tasks.register<JavaExec>("sourcePluginLiveSmokeTest") {
    description = "Runs the non-blocking live smoke report for all published source plugins"
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("tw.wynime.sources.SourcePluginLiveSmokeTestKt")
    isIgnoreExitValue = true
}

val pluginIds = listOf("eacg", "dm1", "next", "girigiri", "2rk", "dida", "dmbus")
val pluginPackageNames = mapOf("2rk" to "rk2")

pluginIds.forEach { pluginId ->
    val taskName = "package${pluginId.replaceFirstChar { it.uppercase() }}Plugin"
    tasks.register<Jar>(taskName) {
        dependsOn(tasks.named("classes"))
        archiveBaseName.set("source-$pluginId")
        destinationDirectory.set(layout.buildDirectory.dir("plugins"))
        from(sourceSets.main.get().output) {
            include("tw/wynime/sources/shared/**")
            include("tw/wynime/sources/${pluginPackageNames[pluginId] ?: pluginId}/**")
        }
        doLast {
            val artifact = layout.projectDirectory.file("artifacts/source-$pluginId.jar").asFile
            artifact.parentFile.mkdirs()
            archiveFile.get().asFile.copyTo(artifact, overwrite = true)
        }
    }
}

tasks.register("packageAllPlugins") {
    dependsOn(pluginIds.map { "package${it.replaceFirstChar { c -> c.uppercase() }}Plugin" })
}

/**
 * Produces Android-compatible plugin jars containing classes.dex.
 * The JVM jars remain the desktop artifacts; Android's DexClassLoader cannot
 * load a jar that only contains JVM class files on recent Android releases.
 */
tasks.register("packageAndroidPlugins") {
    dependsOn(tasks.named("classes"))

    doLast {
        val sdkPath = sequenceOf(
            System.getProperty("android.sdk.dir"),
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("ANDROID_HOME"),
        ).filterNotNull().firstOrNull { it.isNotBlank() }
            ?: error("Set android.sdk.dir, ANDROID_SDK_ROOT, or ANDROID_HOME to package Android plugins")
        val buildTools = File(sdkPath, "build-tools")
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
            ?: error("No Android build-tools directory found under $sdkPath")
        val d8 = listOf(File(buildTools, "d8.bat"), File(buildTools, "d8"))
            .firstOrNull { it.isFile }
            ?: error("D8 was not found under ${buildTools.absolutePath}")

        pluginIds.forEach { pluginId ->
            val jvmArtifact = layout.projectDirectory.file("artifacts/source-$pluginId.jar").asFile
            require(jvmArtifact.isFile) { "Missing desktop artifact: ${jvmArtifact.absolutePath}" }

            val dexDirectory = layout.buildDirectory.dir("android-dex/$pluginId").get().asFile
            project.delete(dexDirectory)
            dexDirectory.mkdirs()
            val d8Process = ProcessBuilder(
                d8.absolutePath,
                "--min-api", "26",
                "--output", dexDirectory.absolutePath,
                jvmArtifact.absolutePath,
            ).inheritIO().start()
            check(d8Process.waitFor() == 0) { "D8 failed for $pluginId" }

            val dexFile = File(dexDirectory, "classes.dex")
            require(dexFile.isFile) { "D8 did not produce classes.dex for $pluginId" }
            val androidArtifact = layout.projectDirectory.file("artifacts/source-$pluginId-android.jar").asFile
            androidArtifact.parentFile.mkdirs()
            JarOutputStream(androidArtifact.outputStream().buffered()).use { output ->
                output.putNextEntry(JarEntry("classes.dex").apply { time = 0L })
                dexFile.inputStream().use { it.copyTo(output) }
                output.closeEntry()
            }
            ZipFile(androidArtifact).use { zip ->
                require(zip.getEntry("classes.dex") != null) {
                    "Android artifact for $pluginId does not contain classes.dex"
                }
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    require(!entries.nextElement().name.endsWith(".class")) {
                        "Android artifact for $pluginId must not contain JVM class files"
                    }
                }
            }
        }
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = input.read(buffer)
        while (count >= 0) {
            if (count > 0) digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun manifestSha256(pluginId: String, platform: String): String {
    val manifest = layout.projectDirectory.file("manifests/$pluginId.json").asFile
    val value = Regex("\\\"$platform\\\"\\s*:\\s*\\{[^}]*\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-fA-F]{64})\\\"")
        .find(manifest.readText())
        ?.groupValues
        ?.get(1)
        ?.lowercase()
        ?: error("Manifest $manifest has no $platform artifact SHA-256")
    return value
}

private fun writeDexJar(dexFile: File, outputFile: File) {
    outputFile.parentFile.mkdirs()
    JarOutputStream(outputFile.outputStream().buffered()).use { output ->
        output.putNextEntry(JarEntry("classes.dex").apply { time = 0L })
        dexFile.inputStream().use { it.copyTo(output) }
        output.closeEntry()
    }
}

val freshnessDesktopTasks = pluginIds.associateWith { pluginId ->
    tasks.register<Jar>("fresh${pluginId.replaceFirstChar { it.uppercase() }}Plugin") {
        dependsOn(tasks.named("classes"))
        archiveBaseName.set("source-$pluginId")
        destinationDirectory.set(layout.buildDirectory.dir("freshness/desktop"))
        from(sourceSets.main.get().output) {
            include("tw/wynime/sources/shared/**")
            include("tw/wynime/sources/${pluginPackageNames[pluginId] ?: pluginId}/**")
        }
    }
}

/**
 * Rebuilds both artifact formats in an isolated build directory and compares them with the
 * repository metadata and checked-in bytes. This is intentionally separate from the packaging
 * tasks, which are allowed to update repository artifacts for a release commit.
 */
val verifyPluginArtifactFreshness = tasks.register("verifyPluginArtifactFreshness") {
    dependsOn(freshnessDesktopTasks.values)
    notCompatibleWithConfigurationCache(
        "The artifact verifier invokes the Android SDK D8 tool during task execution.",
    )

    doLast {
        val indexFile = layout.projectDirectory.file("index.json").asFile
        val indexText = indexFile.readText()
        check(Regex("\\\"pluginApiVersion\\\"\\s*:\\s*2").containsMatchIn(indexText)) {
            "source/plugins/index.json must declare pluginApiVersion=2"
        }
        pluginIds.forEach { pluginId ->
            val manifestFile = layout.projectDirectory.file("manifests/$pluginId.json").asFile
            check(manifestFile.isFile) { "Missing manifest for $pluginId" }
            val manifestText = manifestFile.readText()
            check(Regex("\\\"id\\\"\\s*:\\s*\\\"$pluginId\\\"").containsMatchIn(manifestText)) {
                "Manifest id mismatch for $pluginId"
            }
            check(Regex("\\\"version\\\"\\s*:\\s*\\\"1\\.0\\.25\\\"").containsMatchIn(manifestText)) {
                "Manifest version mismatch for $pluginId"
            }
            check(Regex("\\\"pluginApiVersion\\\"\\s*:\\s*2").containsMatchIn(manifestText)) {
                "Manifest API version mismatch for $pluginId"
            }
            check(Regex("\\\"minHostVersion\\\"\\s*:\\s*\\\"0\\.1\\.3\\\"").containsMatchIn(manifestText)) {
                "Manifest minimum host version mismatch for $pluginId"
            }

            val freshDesktop = freshnessDesktopTasks.getValue(pluginId).get().archiveFile.get().asFile
            val trackedDesktop = layout.projectDirectory.file("artifacts/source-$pluginId.jar").asFile
            check(sha256(freshDesktop) == manifestSha256(pluginId, "desktop")) {
                "Fresh desktop artifact is stale relative to $manifestFile"
            }
            check(sha256(trackedDesktop) == manifestSha256(pluginId, "desktop")) {
                "Tracked desktop artifact is stale relative to $manifestFile"
            }
            ZipFile(freshDesktop).use { zip ->
                check(zip.entries().asSequence().any { it.name.endsWith(".class") }) {
                    "Fresh desktop artifact for $pluginId contains no JVM classes"
                }
                check(zip.entries().asSequence().none { it.name == "classes.dex" }) {
                    "Desktop artifact for $pluginId contains classes.dex"
                }
            }

            val sdkPath = sequenceOf(
                System.getProperty("android.sdk.dir"),
                System.getenv("ANDROID_SDK_ROOT"),
                System.getenv("ANDROID_HOME"),
            ).filterNotNull().firstOrNull { it.isNotBlank() }
                ?: error("Set android.sdk.dir, ANDROID_SDK_ROOT, or ANDROID_HOME for artifact freshness")
            val buildTools = File(sdkPath, "build-tools")
                .listFiles()
                ?.filter(File::isDirectory)
                ?.maxByOrNull(File::getName)
                ?: error("No Android build-tools directory found under $sdkPath")
            val d8 = listOf(File(buildTools, "d8.bat"), File(buildTools, "d8"))
                .firstOrNull(File::isFile)
                ?: error("D8 was not found under ${buildTools.absolutePath}")
            val dexDirectory = layout.buildDirectory.dir("freshness/dex/$pluginId").get().asFile
            project.delete(dexDirectory)
            dexDirectory.mkdirs()
            val d8Process = ProcessBuilder(
                d8.absolutePath,
                "--min-api", "26",
                "--output", dexDirectory.absolutePath,
                freshDesktop.absolutePath,
            ).inheritIO().start()
            check(d8Process.waitFor() == 0) { "D8 failed for fresh $pluginId artifact" }
            val dexFile = File(dexDirectory, "classes.dex")
            check(dexFile.isFile) { "Fresh Android artifact for $pluginId has no classes.dex" }
            val freshAndroid = layout.buildDirectory.file("freshness/android/source-$pluginId-android.jar").get().asFile
            writeDexJar(dexFile, freshAndroid)
            val trackedAndroid = layout.projectDirectory.file("artifacts/source-$pluginId-android.jar").asFile
            check(sha256(freshAndroid) == manifestSha256(pluginId, "android")) {
                "Fresh Android artifact is stale relative to $manifestFile"
            }
            check(sha256(trackedAndroid) == manifestSha256(pluginId, "android")) {
                "Tracked Android artifact is stale relative to $manifestFile"
            }
            ZipFile(freshAndroid).use { zip ->
                val entries = zip.entries().asSequence().toList()
                check(entries.map { it.name } == listOf("classes.dex")) {
                    "Android artifact for $pluginId must contain only classes.dex"
                }
            }
        }
        logger.lifecycle("All ${pluginIds.size} plugin desktop/android artifacts are fresh and SHA-256 consistent")
    }
}

tasks.named("check") {
    dependsOn(verifyPluginArtifactFreshness)
}
