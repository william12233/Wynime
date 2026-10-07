import org.gradle.api.tasks.testing.AbstractTestTask

plugins {
    id("wynime.kmp-compose")
}

kotlin {
    android {
        namespace = "com.wynime.utils.ui.testing"
    }
    compilerOptions {
        optIn.add("androidx.compose.ui.test.ExperimentalTestApi")
    }
    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(projects.utils.testing)
        api(projects.utils.io)
        api(libs.kotlinx.io.core)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.coroutines.test)
        api(libs.compose.ui.test)
        api(kotlin("test"))

        api(libs.compose.runtime)
        implementation(libs.compose.lifecycle.runtime.compose)
        implementation(libs.compose.lifecycle.runtime)
    }
    sourceSets.desktopMain.dependencies {
        implementation(libs.compose.ui.test.junit4)
        runtimeOnly(libs.kotlinx.coroutines.swing)
        api(compose.desktop.currentOs)
    }
    sourceSets.androidMain.dependencies {
        runtimeOnly(libs.kotlinx.coroutines.android)
        implementation(libs.androidx.test.runner)
    }
}

