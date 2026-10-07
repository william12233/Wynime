plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.exploration"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.placeholder)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.named("jvmTest").dependencies {
        implementation(kotlin("reflect"))
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}

compose.resources {
    packageOfResClass = "com.wynime.app.ui.exploration"
}
