plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

    alias(libs.plugins.sentry.kotlin.multiplatform)
}

kotlin {
    android {
        namespace = "com.wynime.app.application"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared)
        api(libs.kotlinx.coroutines.core)
        implementation(libs.atomicfu)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {

        implementation(libs.filekit.dialogs)
    }

}

kotlin {

}
