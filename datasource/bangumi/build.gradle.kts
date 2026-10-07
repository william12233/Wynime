import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.gradle.api.tasks.Sync

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    idea
    alias(libs.plugins.openapi.generator)
}

val generatedRoot = "generated/openapi"

kotlin {
    android {
        namespace = "com.wynime.datasources.bangumi"
    }
    sourceSets.commonMain.dependencies {
        api(projects.datasource.datasourceApi)
        api(libs.kotlinx.datetime)
        api(libs.kotlinx.coroutines.core)
        api(libs.androidx.collection)

        implementation(projects.utils.coroutines)
        implementation(projects.utils.serialization)
        implementation(libs.ktor.client.logging)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
    }
    sourceSets.commonMain {
        kotlin.srcDirs(file("src/commonMain/gen"))
    }
}

idea {
    module {
        generatedSourceDirs.add(file("src/commonMain/gen"))
    }
}

val generateApiV0 = tasks.register("generateApiV0", GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/v0.yaml")
    outputDir.set(layout.buildDirectory.file(generatedRoot).get().asFile.absolutePath)
    packageName.set("com.wynime.datasources.bangumi")
    modelNamePrefix.set("Bangumi")
    apiNameSuffix.set("BangumiApi")

    additionalProperties.set(
        mapOf(
            "apiSuffix" to "BangumiApi",
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",

            "enumPropertyNaming" to "UPPERCASE",

            "omitGradleWrapper" to "true",
        ),
    )
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)

    typeMappings.put(
        "kotlin.Double",
        "@Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum",
    )

}

val stripeApiP1 = tasks.register("stripeApiP1") {
    val strippedP1File = layout.buildDirectory.file("p1-stripped.yaml")
    val inputFile = file("$projectDir/p1.yaml")
    inputs.file(inputFile)
    outputs.file(strippedP1File)

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        val p1ApiObject: Map<String, Any> = inputFile.inputStream().use { yaml.load(it) }

        val paths = p1ApiObject["paths"].cast<Map<String, *>>().toMutableMap()
        val keepPaths = listOf(
            "/p1/episodes/-/comments",
            "/p1/episodes/{episodeID}",
            "/p1/subjects/{subjectID}/comments",
            "/p1/trending/subjects",
            "/p1/collections/subjects",
        )
        val subjectPaths = paths.filter { (path, _) -> keepPaths.any { path.startsWith(it) } }
        println("The following paths are kept: ${subjectPaths.keys}")

        val components = p1ApiObject["components"].cast<Map<String, *>>().toMutableMap()
        components.remove("securitySchemes")
        val keepSchemaKeys = listOf(
            "ErrorResponse",
            "UpdateContent",
            "Episode",
            "Reaction",
            "SlimUser",
            "CommentBase",
            "CreateReply",
            "TurnstileToken",

            "CollectionType",
            "SubjectInterestComment",
            "Reaction",
            "CollectionType",
            "SlimUser",
            "Avatar",
            "SimpleUser",

            "EpisodeCollectionStatus",
            "SlimSubject",
            "EpisodeType",
            "SubjectRating",
            "SubjectType",
            "SubjectImages",
            "SlimSubjectInterest",

            "TrendingSubject",

            "CollectSubject",
            "UpdateSubjectProgress",
            "Subject",
            "Subject.*",
            "Infobox",
            "Slim.*",
            "PersonImages",
            "Reply.*",
            "WikiPlatform",
        )
        val schemas = components["schemas"].cast<Map<String, *>>().toMutableMap()
        val keepSchemas = schemas.filter { (component, _) ->
            keepSchemaKeys.any {
                Regex(it).matchEntire(component) != null
            }
        }

        val strippedApiObject = mutableMapOf<String, Any>().apply {
            put("openapi", p1ApiObject["openapi"].cast())
            put("info", p1ApiObject["info"].cast())
            put("paths", subjectPaths)
            put("components", mapOf("schemas" to keepSchemas))
        }

        strippedP1File.get().asFile.writeText(yaml.dump(strippedApiObject))
    }
}

val generateApiP1 = tasks.register("generateApiP1", GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(stripeApiP1.get().outputs.files.singleFile.absolutePath)
    outputDir.set(layout.buildDirectory.file(generatedRoot).get().asFile.absolutePath)
    packageName.set("com.wynime.datasources.bangumi.next")
    modelNamePrefix.set("BangumiNext")
    apiNameSuffix.set("BangumiNextApi")
    additionalProperties.set(
        mapOf(
            "apiSuffix" to "BangumiNextApi",
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",
            "enumPropertyNaming" to "UPPERCASE",
            "omitGradleWrapper" to "true",
            "generateOneOfAnyOfWrappers" to "true",
        ),
    )
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    validateSpec.set(false)

    typeMappings.put(
        "kotlin.Double",
        "@Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum",
    )

    dependsOn(stripeApiP1)
}

val fixGeneratedOpenApi = tasks.register("fixGeneratedOpenApi") {
    dependsOn(generateApiV0, generateApiP1)
    val models =
        layout.buildDirectory.file("$generatedRoot/src/commonMain/kotlin/com/wynime/datasources/bangumi/models/")
            .get().asFile

    doLast {
        models.resolve("BangumiValue.kt").writeText(
            """
                package com.wynime.datasources.bangumi.models
                
                typealias BangumiValue = kotlinx.serialization.json.JsonElement
            """.trimIndent(),
        )
        models.resolve("BangumiEpisodeCollectionType.kt").delete()
        models.resolve("BangumiSubjectCollectionType.kt").delete()
        models.resolve("BangumiSubjectType.kt").delete()
    }
}

val copyGeneratedToSrc = tasks.register("copyGeneratedToSrc", Sync::class) {
    dependsOn(fixGeneratedOpenApi)
    from(layout.buildDirectory.file("$generatedRoot/src/commonMain/kotlin")) {
        include("com/wynime/**")
    }
    into("src/commonMain/gen")
}

tasks.register("generateOpenApi") {
    dependsOn(copyGeneratedToSrc)
}

commentFreeGeneratedSources("stripGeneratedBangumiComments", file("src/commonMain/gen"), copyGeneratedToSrc)
