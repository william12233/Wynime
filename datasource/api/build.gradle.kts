@file:Suppress("UnstableApiUsage")

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    idea
}

kotlin {
    android {
        namespace = "com.wynime.app.datasource.api"
    }
    sourceSets.commonMain {
        dependencies {
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.protobuf)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(projects.utils.ktorClient)
            api(projects.utils.serialization)
            implementation(projects.utils.platform)
            api(libs.ktor.client.auth)
            implementation(libs.androidx.collection)
            implementation(libs.ktor.client.logging)
            implementation(projects.utils.logging)
        }
    }

    sourceSets.commonTest {
        dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.utils.testing)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            api(libs.jsoup)
        }
    }
}

idea {
    module.generatedSourceDirs.add(file("src/commonTest/kotlin/title/generated"))
}

junitPlatform {
    this.instrumentationTests.enabled = true
}
