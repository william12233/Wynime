plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.subject"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiComment)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.placeholder)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
        implementation(projects.utils.androidxLifecycleRuntimeTesting)
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.ktor.client.mock)
    }
}

compose.resources {
    packageOfResClass = "com.wynime.app.ui.subject"
}
