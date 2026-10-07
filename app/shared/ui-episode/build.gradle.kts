plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.episode"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiComment)
        api(projects.app.shared.uiSubject)
        implementation(projects.app.shared.videoPlayer)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.placeholder)
    }
    sourceSets.commonTest.dependencies {
    }
    sourceSets.named("jvmTest").dependencies {
        implementation(kotlin("reflect"))
    }
    sourceSets.desktopMain.dependencies {
    }
}

