import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    commonMainApi(libs.kotlinx.serialization.json)
    commonMainApi(libs.kotlinx.serialization.protobuf)
}

kotlin {
    android {
        namespace = "com.wynime.utils.serialization"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}