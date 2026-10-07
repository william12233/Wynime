import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    id("wynime.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.skydoves.compose.stability.analyzer")
}

val libs = versionCatalogs.named("libs")

configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(libs.getLibrary("compose-foundation"))
        api(libs.getLibrary("compose-runtime"))
        api(libs.getLibrary("compose-ui"))
        api(libs.getLibrary("compose-animation"))
        api(libs.getLibrary("compose-material3"))
        api(libs.getLibrary("compose-material-icons-extended"))
        api(libs.getLibrary("compose-window-core"))
    }
    sourceSets.commonTest.dependencies {

        implementation(libs.getLibrary("compose-ui-test"))
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.getLibrary("compose-ui-test-junit4"))
    }
    sourceSets.getByName("androidDeviceTest").dependencies {
        implementation(libs.getLibrary("androidx-compose-ui-test-manifest"))
    }
}

extensions.configure<ComposeCompilerGradlePluginExtension> {
    stabilityConfigurationFiles.add {
        rootProject.layout.projectDirectory.file("gradle/sketch-compose-stability.conf").asFile
    }
}

tasks.named("generateComposeResClass") {
    mustRunAfter("generateResourceAccessorsForAndroidHostTest")
}
tasks.withType(KotlinCompilationTask::class).configureEach {
    mustRunAfter(tasks.matching { it.name == "generateComposeResClass" })
    mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidMain" })
    mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidHostTest" })
    mustRunAfter(tasks.matching { it.name == "generateResourceAccessorsForAndroidDeviceTest" })
}

val stabilityInputTaskNames = listOf(
    "compileAndroidMain",
    "compileAndroidHostTest",
    "compileAndroidDeviceTest",
    "compileKotlinDesktop",
    "compileTestKotlinDesktop",
)
tasks.matching {

    it.name.endsWith("stabilityCheck", ignoreCase = true) || it.name.endsWith("stabilityDump", ignoreCase = true)
}.configureEach {
    stabilityInputTaskNames.forEach { taskName ->

        mustRunAfter(tasks.matching { task -> task.name == taskName })
    }
}
