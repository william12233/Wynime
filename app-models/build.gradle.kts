plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android { namespace = "com.wynime.models" }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.json)
    }
}
