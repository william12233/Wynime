import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("wynime.kmp-library")
}

kotlin {
    android {
        namespace = "com.wynime.utils.intellij.annotations"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets.getByName("jvmMain").dependencies {
        api(libs.jetbrains.annotations)
    }
}
