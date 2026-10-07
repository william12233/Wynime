@file:Suppress("UnstableApiUsage")

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.wynime.utils.analytics"
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(projects.utils.logging)
            }
        }
        val skikoMain by getting {
            dependencies {
                api(projects.utils.ktorClient)
                api(projects.utils.serialization)
                api(projects.utils.coroutines)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.firebase.analytics)
            }
        }
        val androidMain by getting {
            dependencies {

                api(libs.google.firebase.common)
                api(libs.google.firebase.analytics)
            }
        }
    }
}
