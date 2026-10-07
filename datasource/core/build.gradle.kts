plugins {
    id("wynime.kmp-jvm-only")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            api(libs.kotlinx.coroutines.core)
            api(projects.datasource.datasourceApi)
            api(projects.utils.ktorClient)
            api(projects.utils.io)
        }
    }
}
