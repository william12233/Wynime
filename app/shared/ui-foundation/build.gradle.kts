plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.foundation"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appData)
        api(projects.app.shared.appPlatform)
        api(projects.utils.uiPreview)
        api(projects.utils.platform)
        api(projects.app.shared.appLang)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.serialization.protobuf)
        implementation(projects.app.shared.placeholder)

        api(libs.sketch.compose.core)
        implementation(libs.sketch.http.core)
        implementation(libs.sketch.svg)
        api(libs.zoomimage.compose.sketch4.core)
        implementation(libs.filekit.dialogs)
        implementation(libs.filekit.dialogs.compose)

        implementation(libs.compose.components.resources)
        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.navigation3.runtime)
        api(libs.compose.navigation3.ui)
        api(libs.compose.lifecycle.viewmodel.navigation3)
        api(libs.compose.material3.adaptive.core.get().toString())
        api(libs.compose.material3.adaptive.layout.get().toString())
        api(libs.compose.material3.adaptive.navigation0.get().toString())

        implementation(projects.utils.bbcode)
        implementation(libs.constraintlayout.compose)
        api(projects.app.shared.pagingCompose)

        api(libs.koin.core)
        api(libs.atomicfu)

        api(libs.materialkolor)
        api(libs.kmpalette.core)
        api(libs.haze)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
        implementation(projects.utils.androidxLifecycleRuntimeTesting)
        implementation(libs.ktor.client.mock)
    }
    sourceSets.androidMain.dependencies {
        api(libs.compose.material3.adaptive.core)

    }
    sourceSets.desktopMain.dependencies {
        implementation(libs.jna)
        implementation(libs.jna.platform)
        api(libs.directories)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.wynime.app.ui.foundation"
}
