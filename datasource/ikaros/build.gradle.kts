plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    idea
}

kotlin {
    android {
        namespace = "com.wynime.datasources.ikaros"
    }
}

dependencies {
    commonMainApi(projects.datasource.datasourceApi)
    commonMainImplementation(projects.utils.logging)
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
