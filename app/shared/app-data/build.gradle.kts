plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

    alias(libs.plugins.kotlin.parcelize)

    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
    idea
}

kotlin {
    android {
        namespace = "com.wynime.app.data"
    }
    sourceSets.commonMain.dependencies {
        implementation(projects.app.shared.appPlatform)
        implementation(projects.app.shared.appLang)
        implementation(projects.utils.intellijAnnotations)
        implementation(libs.compose.components.resources)
        api(projects.app.shared.videoPlayer.videoPlayerApi)
        api(libs.mediamp.api)
        api(libs.mediamp.source.ktxio)
        implementation(libs.kotlinx.serialization.json.io)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.core)
        api(libs.kotlinx.collections.immutable)
        implementation(libs.kotlinx.serialization.json)
        implementation(projects.utils.io)
        implementation(projects.utils.coroutines)
        api(projects.utils.xml)
        api(projects.utils.coroutines)
        api(projects.appModels)
        implementation(projects.cloudClient)
        api(projects.utils.ipParser)
        api(projects.utils.jsonpath)
        api(projects.utils.httpDownloader)
        api(projects.utils.serialization)
        api(projects.source.pluginApi)

        api(libs.datastore.core)
        api(libs.datastore.preferences.core)
        api(libs.androidx.room.runtime)
        api(libs.androidx.room.paging)
        api(libs.sqlite.bundled)

        api(projects.datasource.datasourceApi)
        api(projects.datasource.datasourceCore)
        api(projects.datasource.bangumi)
        api(projects.datasource.jellyfin)
        api(projects.datasource.ikaros)

        api(libs.paging.common)

        implementation(libs.koin.core)
        implementation(libs.atomicfu)
        implementation(libs.ktor.network)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.mediamp.test)
        implementation(projects.utils.uiTesting)
        implementation(projects.utils.androidxLifecycleRuntimeTesting)
        implementation(libs.ktor.client.mock)
        implementation(libs.turbine)
        implementation(kotlin("reflect"))
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.slf4j.simple)
    }
    sourceSets.getByName("desktopMain").dependencies {
        implementation(libs.opencc4j)
    }
    sourceSets.desktopMain {
        dependencies {
            implementation(libs.onnxruntime)

            if (getOs() == Os.Windows && getArch() == Arch.AARCH64) {

                runtimeOnly(projects.ciHelper.sqliteWoa64)
            }
        }
    }
    sourceSets.getByName("androidDeviceTest").dependencies {

        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.exoplayer.hls)
    }
    sourceSets.desktopTest {

        resources.srcDir("src/androidDeviceTest/assets")
        dependencies {
            implementation("androidx.room:room-testing:${libs.versions.room.get()}")
        }
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.opencc4j)
        implementation(libs.androidx.browser)
        implementation(libs.onnxruntime.android)
        api(libs.datastore)
        api(libs.datastore.preferences)
        api(libs.androidx.lifecycle.runtime.ktx)
        api(libs.androidx.lifecycle.service)
        api(libs.androidx.lifecycle.process)
    }

}

compose.resources {
    packageOfResClass = "com.wynime.app.data"
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    kspDesktop(libs.androidx.room.compiler)
    kspAndroid(libs.androidx.room.compiler)

}

val pluginResourceRoot = rootProject.layout.projectDirectory.dir("source/plugins")
val bundledPluginManifests = tasks.register<Copy>("copyBundledPluginManifests") {
    from(pluginResourceRoot.dir("manifests"))
    into("src/commonMain/composeResources/files/source-plugins/manifests")
}
val bundledAndroidPlugins = tasks.register<Copy>("copyBundledAndroidPlugins") {
    from(pluginResourceRoot.dir("artifacts")) { include("source-*-android.jar") }
    into("src/androidMain/composeResources/files/source-plugins/artifacts")
    dependsOn(bundledPluginManifests)
}
val bundledWindowsPlugins = tasks.register<Copy>("copyBundledWindowsPlugins") {
    from(pluginResourceRoot.dir("artifacts")) { include("source-*.jar"); exclude("*-android.jar") }
    into("src/desktopMain/composeResources/files/source-plugins/artifacts")
    dependsOn(bundledPluginManifests)
}
tasks.matching { it.name in setOf("prepareComposeResourcesTaskForCommonMain", "copyNonXmlValueResourcesForCommonMain", "convertXmlValueResourcesForCommonMain") }.configureEach {
    dependsOn(bundledPluginManifests)
}
tasks.matching { it.name in setOf("prepareComposeResourcesTaskForAndroidMain", "copyNonXmlValueResourcesForAndroidMain", "convertXmlValueResourcesForAndroidMain") }.configureEach {
    dependsOn(bundledAndroidPlugins)
}
tasks.matching { it.name in setOf("prepareComposeResourcesTaskForDesktopMain", "copyNonXmlValueResourcesForDesktopMain", "convertXmlValueResourcesForDesktopMain") }.configureEach {
    dependsOn(bundledWindowsPlugins)
}
