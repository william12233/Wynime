plugins {
    id("wynime.kmp-compose")
}

group = "org.burnoutcrew.composereorderable"
version = "0.9.7"

kotlin {
    android {
        namespace = "com.wynime.app.reorderable"
    }
    sourceSets {
        val commonMain by getting {
            dependencies {

                implementation(libs.compose.foundation)
                implementation(libs.compose.animation)
                implementation(libs.compose.ui.util)
            }
        }
    }
}