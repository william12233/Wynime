plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.comment"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        implementation(projects.app.shared.appData)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.placeholder)
        implementation(projects.utils.bbcode)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
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
    packageOfResClass = "com.wynime.app.ui.comment"
}
