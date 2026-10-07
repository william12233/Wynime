import java.util.Properties

rootProject.name = "Wynime"

pluginManagement {

    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {

    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    @Suppress("UnstableApiUsage")
    repositories {

        mavenCentral()
        google()
        mavenLocal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://jogamp.org/deployment/maven")
    }
}

plugins {
    id("com.gradle.develocity") version "4.3.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

develocity {
    buildScan {

        publishing.onlyIf { !System.getenv("CI").isNullOrEmpty() }
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        uploadInBackground = System.getenv("CI").isNullOrEmpty()
    }
}

fun includeProject(projectPath: String, dir: String? = null) {
    include(projectPath)
    if (dir != null) project(projectPath).projectDir = file(dir)
}

includeProject(":utils:platform")
includeProject(":utils:intellij-annotations")
includeProject(":utils:logging")
includeProject(":utils:serialization", "utils/serialization")
includeProject(":utils:coroutines", "utils/coroutines")
includeProject(":utils:ktor-client", "utils/ktor-client")
includeProject(":utils:io", "utils/io")
includeProject(":utils:testing", "utils/testing")
includeProject(":utils:xml")
includeProject(":utils:jsonpath")
includeProject(":utils:bbcode", "utils/bbcode")
includeProject(":utils:bbcode:test-codegen")
includeProject(":utils:ip-parser", "utils/ip-parser")
includeProject(":utils:ui-testing")
includeProject(":utils:androidx-lifecycle-runtime-testing")
includeProject(":utils:ui-preview")
includeProject(":utils:analytics")
includeProject(":utils:http-downloader")
includeProject(":utils:build-config")
includeProject(":utils:video-enhancement-shader-provider")
includeProject(":utils:selector-workflow")

includeProject(":app:shared")

includeProject(":app:shared:app-platform")
includeProject(":app:shared:app-data")
includeProject(":app:shared:app-lang")
includeProject(":app:shared:ui-foundation")

includeProject(":app:shared:ui-settings")

includeProject(":app:shared:ui-adaptive")
includeProject(":app:shared:ui-subject")

includeProject(":app:shared:ui-download")
includeProject(":app:shared:ui-exploration")

includeProject(":app:shared:ui-comment")
includeProject(":app:shared:ui-onboarding")

includeProject(":app:shared:ui-mediaselect")
includeProject(":app:shared:ui-episode")

includeProject(":app:shared:ui-exprovider")
includeProject(":app:shared:video-player:video-player-api", "app/shared/video-player/api")
includeProject(":app:shared:video-player")
includeProject(":app:shared:application")

includeProject(":app:shared:placeholder", "app/shared/thirdparty/placeholder")
includeProject(":app:shared:paging-compose", "app/shared/thirdparty/paging-compose")
includeProject(":app:shared:reorderable", "app/shared/thirdparty/reorderable")

includeProject(":app:desktop", "app/desktop")
includeProject(":app:android", "app/android")

includeProject(":app-models")

includeProject(":source:plugin-api", "source/plugin-api")
includeProject(":source:plugins", "source/plugins")

includeProject(":datasource:datasource-api", "datasource/api")
includeProject(
    ":datasource:datasource-core",
    "datasource/core",
)
includeProject(":datasource:bangumi", "datasource/bangumi")

includeProject(":datasource:jellyfin", "datasource/jellyfin")
includeProject(":datasource:ikaros", "datasource/ikaros")

includeProject(":ci-helper", "ci-helper")
includeProject(
    ":ci-helper:sqlite-woa64",
    "ci-helper/sqlite-woa64",
)
includeProject(":tools:datasource-test-mcp", "tools/datasource-test-mcp")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val localProperties: Provider<Properties> =
    providers.fileContents(layout.settingsDirectory.file("local.properties")).asText
        .map { text -> Properties().apply { text.reader().use { load(it) } } }

fun findLocalProperty(key: String): String? = localProperties.orNull?.getProperty(key)

findLocalProperty("wynime.build.mediamp.path")?.let { mediampPath ->
    println("i:: Including mediamp as a Composite Build from: $mediampPath")
    includeBuild(mediampPath) {
        dependencySubstitution {
            substitute(module("org.openani.mediamp:mediamp-api"))
                .using(project(":mediamp-api"))
            substitute(module("org.openani.mediamp:mediamp-exoplayer"))
                .using(project(":mediamp-exoplayer"))
            substitute(module("org.openani.mediamp:mediamp-mpv"))
                .using(project(":mediamp-mpv"))

            substitute(module("org.openani.mediamp:mediamp-test"))
                .using(project(":mediamp-test"))
            substitute(module("org.openani.mediamp:mediamp-source-ktxio"))
                .using(project(":mediamp-source-ktxio"))

        }
    }
}

includeProject(":cloud-client")

includeProject(":app:dev-preview")
