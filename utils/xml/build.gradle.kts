plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.wynime.utils.xml"
    }
    sourceSets.commonMain.dependencies {
        api(projects.utils.io)
    }

    sourceSets.getByName("jvmMain").dependencies {
        api(libs.jsoup)
    }

}
