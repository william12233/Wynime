plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.utils.http.downloader"
        packaging {
            resources {
                pickFirsts.add("META-INF/AL2.0")
                pickFirsts.add("META-INF/LGPL2.1")
                excludes.add("META-INF/DEPENDENCIES")
                excludes.add("META-INF/licenses/ASM")
            }
        }
    }
    sourceSets.commonMain.dependencies {
        api(projects.utils.coroutines)
        api(libs.kotlinx.datetime)
        api(libs.mediamp.ffmpeg)
        implementation(projects.utils.logging)
        implementation(projects.utils.ktorClient)
        api(libs.datastore.core)
        implementation(libs.androidx.room.common)
        implementation(projects.utils.serialization)
        implementation(libs.kotlinx.serialization.protobuf)
        implementation(libs.kotlinx.collections.immutable)
    }
    sourceSets.desktopMain.dependencies {

    }
    sourceSets.commonTest.dependencies {
        implementation(libs.turbine)
        implementation(libs.ktor.client.mock)
        implementation(libs.ktor.server.test.host)
        runtimeOnly(libs.slf4j.simple)
    }
}

dependencies {
    when (val triple = getOsTriple()) {
        "windows-x64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)

        "windows-arm64" -> desktopTestRuntimeOnly(libs.mediamp.ffmpeg.runtime.windows.arm64)
        else -> {}
    }
}
