import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "tw.wynime.sources"
version = "1.0.24"

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
