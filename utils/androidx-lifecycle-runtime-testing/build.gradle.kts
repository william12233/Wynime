plugins {
    id("wynime.kmp-library")
}

kotlin {
    android {
        namespace = "com.wynime.utils.androidx.lifecycle.runtime.testing"
    }
    explicitApiWarning()
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.compose.lifecycle.runtime)
    }
}
