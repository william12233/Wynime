plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.wynime.app.ui.onboarding"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiSettings)
        implementation(projects.utils.ktorClient)
        implementation(libs.compose.components.resources)
        implementation(projects.utils.logging)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {

        implementation(libs.androidx.camera.camera2)
        implementation(libs.androidx.camera.lifecycle)
        implementation(libs.androidx.camera.view)
        implementation(libs.zxing.core)
        implementation(libs.androidx.activity.compose)
    }
    sourceSets.desktopMain.dependencies {
    }
}

compose.resources {
    packageOfResClass = "com.wynime.app.ui.onboarding"
}
