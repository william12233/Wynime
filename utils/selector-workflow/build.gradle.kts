plugins {
    id("wynime.kmp-compose")
}

kotlin {
    android {
        namespace = "com.wynime.utils.selectorworkflow"
    }
    sourceSets.commonMain.dependencies {
        api(libs.compose.lifecycle.viewmodel)
        implementation(libs.kotlinx.collections.immutable)
        implementation(projects.utils.uiPreview)
    }

    sourceSets.getByName("desktopTest").dependencies {
        implementation(compose.desktop.currentOs)
    }
}
