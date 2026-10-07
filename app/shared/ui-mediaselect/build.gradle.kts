plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.mediaselect"
    }

    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiSettings)
        implementation(libs.atomicfu)
        implementation(projects.utils.ktorClient)
        implementation(libs.compose.components.resources)
        implementation(projects.utils.logging)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}
