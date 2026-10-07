plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.video.player"
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appPlatform)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.videoPlayer.videoPlayerApi)
        api(libs.mediamp.api)
        api(libs.kotlinx.coroutines.core)
        api(projects.utils.coroutines)
        implementation(projects.utils.videoEnhancementShaderProvider)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.compose.material3.adaptive.core)
        implementation(libs.androidx.media3.ui)
        implementation(libs.androidx.media3.effect)
        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.exoplayer.dash)
        implementation(libs.androidx.media3.exoplayer.hls)
        implementation(libs.libass.media)
        api(libs.mediamp.exoplayer)
    }
    sourceSets.desktopMain.dependencies {
        api(compose.desktop.currentOs) {
            exclude("org.jetbrains.compose.material:material")
        }

        api(libs.kotlinx.coroutines.swing)

        api(libs.mediamp.mpv)
    }

}
