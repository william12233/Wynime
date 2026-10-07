plugins {
    id("wynime.kmp-compose")
}

kotlin {
    android {
        namespace = "com.wynime.utils.ui.preview"
    }
    sourceSets.commonMain.dependencies {
        api(libs.androidx.annotation)
        api(libs.compose.ui.tooling.preview)
    }
}