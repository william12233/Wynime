import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("wynime.kmp-library")

    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.wynime.utils.io"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xdont-warn-on-error-suppression")

    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(libs.kotlinx.io.core)
        implementation(projects.utils.logging)
        implementation(libs.atomicfu)

    }

}
