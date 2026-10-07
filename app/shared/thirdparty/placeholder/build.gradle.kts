plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.placeholder"
    }
    sourceSets.commonMain.dependencies {
        implementation(libs.androidx.annotation)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.compose.material3.adaptive.core)
    }
}
