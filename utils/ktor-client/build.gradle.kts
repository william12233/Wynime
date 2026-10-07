plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.wynime.utils.ktor.client"
    }
    sourceSets.commonMain {
        dependencies {
            api(libs.kotlinx.serialization.core)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            implementation(projects.utils.xml)
            api(projects.utils.logging)
            implementation(projects.utils.platform)
            api(projects.utils.io)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            api(libs.ktor.client.okhttp)
            implementation(libs.jsoup)
        }
    }

}