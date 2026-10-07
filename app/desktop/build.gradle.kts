import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.gradle.api.tasks.Copy
import java.util.UUID

plugins {
    id("wynime.jvm-library")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    idea
}

dependencies {
    implementation(projects.app.shared)
    implementation(projects.app.shared.uiFoundation)
    implementation(projects.app.shared.application)
    implementation(projects.utils.videoEnhancementShaderProvider)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.native.tray)
    implementation(libs.log4j.core)
    implementation(libs.jsystemthemedetector)
    implementation(libs.bytebuddy.agent)
    implementation(libs.bytebuddy)
    implementation(libs.mediamp.ffmpeg.desktop)

    if (getLocalProperty("wynime.build.mediamp.path") != null) {

        if (getLocalProperty("wynime.build.mediamp.mpv.devNativeDir") == null) {
            runtimeOnly(libs.mediamp.mpv) {
                capabilities {
                    requireCapability("org.openani.mediamp:mediamp-mpv-runtime-${getOsTriple()}")
                }
            }
        }
    } else {
        when (val triple = getOsTriple()) {
            "windows-x64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
            "windows-arm64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.arm64)

            else -> {}
        }
    }

    when (val triple = getOsTriple()) {
        "windows-x64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)

        "windows-arm64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.arm64)
        else -> {}
    }

}

listOf("compileClasspath", "runtimeClasspath").forEach { name ->
    configurations.named(name) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui-test-junit4")
    }
}

tasks.named("processResources") {
    dependsOn(":app:shared:desktopProcessResources")
    dependsOn(":app:shared:ui-foundation:desktopProcessResources")
}

sourceSets {
    main {
        resources.srcDirs(
            project(projects.app.shared.path).layout.buildDirectory
                .file("processedResources/desktop/main"),
            project(projects.app.shared.uiFoundation.path).layout.buildDirectory
                .file("processedResources/desktop/main"),
        )
    }
}

compose.desktop {
    application {

        val legacyZgcFlagsSupported = System.getProperty("java.specification.version")
            .toIntOrNull()
            ?.let { it in 21..23 }
            ?: false
        if (legacyZgcFlagsSupported) {
            jvmArgs(
                "-XX:+UseZGC",
                "-XX:+ZGenerational",
            )
        }
        jvmArgs(
            "-XX:SoftMaxHeapSize=512m",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=TRACE",
            "-Dsun.java2d.metal=true",
            "-Djogamp.debug.JNILibLoader=true",

            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "-XX:+EnableDynamicAgentLoading",
            "--enable-native-access=ALL-UNNAMED",
            "--enable-native-access=jcef",
        )

        mainClass = "com.wynime.app.desktop.WynimeDesktop"
        nativeDistributions {
            System.getenv("WYNIME_COMPOSE_JAVA_HOME")?.let {
                javaHome = it
            }
            modules(
                "jdk.unsupported",
                "java.management",
                "java.net.http",
                "jcef",
                "gluegen.rt",
                "jogl.all",
                "java.instrument",
                "jdk.security.auth",
            )

            appResourcesRootDir.set(file("appResources"))

            packageName = "Wynime"
            description = project.description
            vendor = "Wynime"

            val projectVersion = project.version.toString()

            windows {
                this.upgradeUuid = UUID.randomUUID().toString()
                iconFile.set(file("icons/a_1024x1024_rounded.ico"))
            }

            licenseFile.set(layout.settingsDirectory.file("LICENSE.txt"))
            packageVersion = providers.gradleProperty("package.version").get()
        }

        if (getLocalProperty("wynime.desktop.proguard")?.toBooleanStrict() != false) {
            buildTypes.release.proguard {
                isEnabled.set(true)

                version = "7.9.1"
                optimize.set(true)
                obfuscate.set(false)
                this.configurationFiles.from(project(":app:shared").sharedAndroidProguardRules())
                this.configurationFiles.from(file("proguard-desktop.pro"))
            }
        }
    }
}

afterEvaluate {
    val os = getOs()
    when (os) {
        Os.Windows -> {
            tasks.named("createRuntimeImage", AbstractJLinkTask::class) {
                val dirsNames = listOf(

                    "bin/jcef_helper.exe" to "bin/jcef_helper.exe",
                    "bin/icudtl.dat" to "bin/icudtl.dat",
                    "bin/chrome_100_percent.pak" to "bin/chrome_100_percent.pak",
                    "bin/chrome_200_percent.pak" to "bin/chrome_200_percent.pak",
                    "bin/resources.pak" to "bin/resources.pak",
                    "bin/v8_context_snapshot.bin" to "bin/v8_context_snapshot.bin",
                )
                val optionalJcefRuntimeFiles = setOf(
                    "bin/chrome_100_percent.pak",
                    "bin/chrome_200_percent.pak",
                    "bin/resources.pak",
                )

                dirsNames.forEach { (sourcePath, destPath) ->
                    val source = File(javaHome.get()).resolve(sourcePath)
                    if (sourcePath in optionalJcefRuntimeFiles && !source.exists()) {
                        logger.info("Skipped missing optional JCEF runtime file $source")
                        return@forEach
                    }
                    inputs.file(source)
                    val dest = destinationDir.file(destPath)
                    outputs.file(dest)
                    doLast("copy $sourcePath") {
                        source.copyTo(dest.get().asFile)
                        logger.info("Copied $source to $dest")
                    }
                }

                val localesSource = File(javaHome.get()).resolve("bin/locales")
                val localesDest = destinationDir.dir("bin/locales")
                if (localesSource.exists()) {
                    inputs.dir(localesSource)
                    outputs.dir(localesDest)
                    doLast("copy locales") {
                        localesSource.copyRecursively(localesDest.get().asFile, overwrite = true)
                        logger.info("Copied $localesSource to $localesDest")
                    }
                }
            }
        }

        else -> {}
    }
}

tasks.withType(KotlinCompilationTask::class) {
    mustRunAfter("generateComposeResClass")
}

afterEvaluate {
    tasks.named("createReleaseDistributable", AbstractJPackageTask::class) {
        finalizedBy(copyReleaseLicenseNotices)
        doLast {
            unpackComposeDesktopNativeLibraries()
        }
    }
}

val copyReleaseLicenseNotices = tasks.register<Copy>("copyReleaseLicenseNotices") {
    from(rootProject.file("licenses"))
    into(layout.buildDirectory.dir("compose/binaries/main-release/app/Wynime/licenses"))
}

idea {
    module {
        excludeDirs.add(file("appResources/windows-x64/lib"))
        excludeDirs.add(file("test-sandbox"))
    }
}

tasks.withType<ComposeHotRun> {
    configureDevProperties()
}

afterEvaluate {
    tasks.named("run", JavaExec::class) {
        configureDevProperties()
    }
}

fun JavaExec.configureDevProperties() {

    mainClass.set(
        providers.gradleProperty("wynime.desktop.mainClass").getOrElse("com.wynime.app.desktop.WynimeDesktop"),
    )
    this.jvmArgs(

        "-Xmx512m",
        "-XX:+EnableDynamicAgentLoading",
    )
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
    systemProperty("kotlinx.coroutines.debug", "on")
    systemProperty("wynime.debug", "true")

    systemProperty(
        "wynime.windows.nativeTouch.debug",
        providers.gradleProperty("wynime.windows.nativeTouch.debug").getOrElse("false"),
    )

    getLocalProperty("wynime.build.mediamp.mpv.devNativeDir")?.let {
        systemProperty("mediamp.mpv.dev.native.dir", it)
    }

    systemProperty("wynime.mpv.selftest", providers.gradleProperty("wynime.mpv.selftest").getOrElse("false"))
    workingDir(file("test-sandbox"))
}
