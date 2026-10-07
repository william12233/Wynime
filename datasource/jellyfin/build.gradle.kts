plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.datasources.jellyfin"
    }
    sourceSets.commonTest {
        dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

dependencies {
    commonMainApi(projects.datasource.datasourceApi)
    commonMainImplementation(projects.utils.serialization)
    commonMainImplementation(projects.utils.logging)

    commonMainApi(libs.kotlinx.coroutines.core)
    commonMainImplementation(libs.ktor.client.logging)
    commonMainImplementation(libs.ktor.client.content.negotiation)
    commonMainImplementation(libs.ktor.serialization.kotlinx.json)
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
