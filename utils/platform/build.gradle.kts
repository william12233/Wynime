import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.utils.platform"
    }
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets.commonMain.dependencies {
        implementation(libs.atomicfu)
        implementation(libs.kotlinx.collections.immutable)
        implementation(libs.androidx.collection)
    }

    sourceSets.getByName("jvmMain").dependencies {
        api(libs.jetbrains.annotations)
    }

}
