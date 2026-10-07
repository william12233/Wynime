plugins {
    id("wynime.kmp-compose")
}

kotlin {
    android {
        namespace = "com.wynime.app.paging.compose"
    }
    sourceSets.commonMain.dependencies {
        implementation(libs.paging.common)
        implementation(libs.compose.lifecycle.runtime.compose)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.compose.material3.adaptive.core)
    }
}
