import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("wynime.kmp-library")
}

dependencies {
    jvmMainApi(libs.slf4j.api)
}

kotlin {
    android {
        namespace = "com.wynime.utils.logging"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

}