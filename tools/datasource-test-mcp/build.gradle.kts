plugins {
    id("wynime.jvm-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.compose)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.datasource.datasourceApi)
    implementation(projects.app.shared.appData)
    implementation(projects.app.shared.appPlatform)
    implementation(projects.utils.ktorClient)
    implementation(projects.utils.logging)
    implementation(projects.utils.serialization)
    implementation(projects.utils.xml)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    implementation(libs.mediamp.api)
    implementation(libs.mediamp.mpv)

    implementation(
        "org.jetbrains.compose.desktop:desktop-jvm-${getOsTriple()}:${libs.versions.compose.multiplatform.get()}",
    )

    implementation(libs.mediamp.ffmpeg.desktop)

    when (val triple = getOsTriple()) {
        "windows-x64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)
        "windows-arm64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.arm64)
        else -> {}
    }

    if (getLocalProperty("wynime.build.mediamp.path") != null) {
        runtimeOnly(libs.mediamp.mpv) {
            capabilities {
                requireCapability("org.openani.mediamp:mediamp-mpv-runtime-${getOsTriple()}")
            }
        }
    } else {
        when (val triple = getOsTriple()) {
            "windows-x64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
            "windows-arm64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.arm64)
            else -> {}
        }
    }

    runtimeOnly(libs.slf4j.simple)

    runtimeOnly(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.jupiter.api)
    testImplementation(libs.junit5.jupiter.params)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "com.wynime.tools.datasourcetestmcp.MainKt"
}
