plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.video.player.api"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(libs.mediamp.api)
        api(projects.utils.coroutines)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.desktopMain.dependencies {
    }

}
