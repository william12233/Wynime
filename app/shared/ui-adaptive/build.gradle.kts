plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.adaptive"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)

        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.material3.adaptive.core)
        api(libs.compose.material3.adaptive.layout)
        api(libs.compose.material3.adaptive.navigation0)
        api(libs.compose.material3.adaptive.navigation.suite)

        api(libs.koin.core)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        api(libs.compose.material3.adaptive.core)
        api(libs.androidx.compose.material3.adaptive)
        api(libs.androidx.compose.material3.adaptive.layout)
        api(libs.androidx.compose.material3.adaptive.navigation)

    }
}
