plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.download"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiSettings)
        api(projects.app.shared.uiMediaselect)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.placeholder)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}

compose.resources {
    packageOfResClass = "com.wynime.app.ui.download"
}
