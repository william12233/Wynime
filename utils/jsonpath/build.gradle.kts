plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.wynime.utils.jsonpath"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.json)
        api(libs.jsonpathkt.kotlinx)

        implementation(projects.utils.intellijAnnotations)
    }
}
