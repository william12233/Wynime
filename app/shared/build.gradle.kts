@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.ProguardFiles.getDefaultProguardFile

plugins {
    id("wynime.kmp-compose")

    alias(libs.plugins.kotlin.plugin.serialization)

    alias(libs.plugins.sentry.kotlin.multiplatform)
    alias(libs.plugins.aboutlibraries)
    idea
}

kotlin {
    android {
        namespace = "com.wynime"
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")

        optimization {
            minify = false
            keepRules.apply {
                files(
                    getDefaultProguardFile("proguard-android-optimize.txt", layout.buildDirectory),
                    *sharedAndroidProguardRules(),
                )
            }
        }

    }

    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(projects.utils.intellijAnnotations)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
        api(libs.kotlinx.serialization.json.io)
        api(libs.kotlinx.serialization.protobuf)
        implementation(libs.atomicfu)
        api(libs.kotlinx.datetime)
        api(libs.kotlinx.io.core)
        api(libs.kotlinx.collections.immutable)
        api(projects.utils.ktorClient)

        api(projects.app.shared.appPlatform)
        api(projects.app.shared.appData)
        api(projects.app.shared.placeholder)
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.videoPlayer)
        api(projects.app.shared.uiSettings)
        api(projects.app.shared.uiExploration)
        api(projects.app.shared.uiSubject)
        api(projects.app.shared.uiDownload)
        api(projects.app.shared.uiAdaptive)
        api(projects.app.shared.uiOnboarding)
        api(projects.app.shared.uiEpisode)
        api(projects.app.shared.uiMediaselect)
        api(projects.app.shared.uiExprovider)

        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.navigation3.runtime)
        api(libs.compose.navigation3.ui)
        api(libs.compose.lifecycle.viewmodel.navigation3)
        api(libs.compose.material3.adaptive.navigation.suite)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.reorderable)

        api(projects.datasource.datasourceApi)
        api(projects.datasource.datasourceCore)
        api(projects.datasource.bangumi)

        api(projects.appModels)
        api(projects.utils.logging)
        api(projects.utils.coroutines)
        api(projects.utils.io)
        api(projects.utils.xml)
        api(projects.utils.bbcode)
        api(projects.utils.ipParser)

        api(libs.ktor.client.websockets)
        api(libs.ktor.client.logging)
        api(libs.ktor.client.content.negotiation)
        api(libs.ktor.serialization.kotlinx.json)

        api(libs.koin.core)
        implementation(libs.constraintlayout.compose)
    }

    sourceSets.jvmMain.dependencies {

        api(projects.datasource.jellyfin)
        api(projects.datasource.ikaros)

        implementation(libs.jna)
        implementation(libs.slf4j.api)
        api(libs.ktor.client.okhttp)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.mediamp.test)
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.testing)
        implementation(projects.utils.uiTesting)
        implementation(libs.turbine)
    }

    sourceSets.androidMain.dependencies {
        api(libs.kotlinx.coroutines.android)
        api(libs.datastore)
        api(libs.datastore.preferences)
        api(libs.androidx.appcompat)
        api(libs.androidx.media)
        api(libs.androidx.core.ktx)
        api(libs.androidx.activity.compose)
        api(libs.androidx.activity.ktx)
        api(libs.koin.android)
        implementation(libs.androidx.browser)
        api(libs.logback.android)
        api(projects.utils.buildConfig)
    }

    sourceSets.androidHostTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.testing)
        implementation(projects.utils.uiTesting)
        implementation(libs.turbine)
        implementation(libs.mockito)
        implementation(libs.mockito.kotlin)
        implementation(libs.koin.test)
    }

    sourceSets.desktopMain.dependencies {
        api(compose.desktop.currentOs) {
            exclude("org.jetbrains.compose.material:material")
            exclude("org.jetbrains.compose.ui:ui-tooling-preview")
        }
        api(libs.compose.ui.graphics.desktop)
        api(projects.utils.logging)
        api(libs.kotlinx.coroutines.swing)
        implementation(libs.jna)

        implementation(libs.log4j.core)
        implementation(libs.log4j.slf4j.impl)

        implementation(libs.ktor.serialization.kotlinx.json)
    }
}

val aboutLibrariesExportedJson = layout.buildDirectory.file("generated/aboutLibrariesExport/aboutlibraries.json")
val mergeCommonMainComposeResources = tasks.register<Sync>("mergeCommonMainComposeResources") {
    description = "Merge "
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    from(aboutLibrariesExportedJson) { into("files") }
    dependsOn("exportLibraryDefinitions")
    into(layout.buildDirectory.dir("generated/mergedCommonMainComposeResources"))
}

compose.resources {

    packageOfResClass = "com.wynime.app.shared"
    generateResClass = always

    customDirectory(
        "commonMain",
        mergeCommonMainComposeResources.map {
            layout.buildDirectory.dir("generated/mergedCommonMainComposeResources").get()
        },
    )
}

aboutLibraries {
    export {
        outputFile = aboutLibrariesExportedJson
        prettyPrint = true
    }
    library {

        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

idea {
    val generatedResourcesDir = file("build/generated/compose/resourceGenerator/kotlin")
    module {
        generatedSourceDirs.add(generatedResourcesDir.resolve("commonMainResourceAccessors"))
        generatedSourceDirs.add(generatedResourcesDir.resolve("commonResClass"))
    }
}

afterEvaluate {
    tasks.matching { it.name.contains("generateReleaseLintVitalModel") }.configureEach {
        mustRunAfter("releaseAssetsCopyForAGP")
    }
    tasks.matching { it.name.contains("lintVitalAnalyzeRelease") }.configureEach {
        mustRunAfter("releaseAssetsCopyForAGP")
    }
}

dependencies {
}

