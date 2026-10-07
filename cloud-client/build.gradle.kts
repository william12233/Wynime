import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("wynime.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.openapi.generator)
}

kotlin {
    android { namespace = "com.wynime.cloud" }
    sourceSets.commonMain {
        kotlin.srcDir("src/commonMain/gen")
        dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

val generateCloudApi = tasks.register<GenerateTask>("generateCloudApi") {
    generatorName.set("kotlin")
    inputSpec.set(rootProject.file("backend/wynime-bangumi-broker/openapi.json").absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    packageName.set("com.wynime.cloud")
    additionalProperties.set(mapOf("library" to "multiplatform", "dateLibrary" to "kotlinx-datetime", "omitGradleWrapper" to "true"))
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

val copyCloudApi = tasks.register<Copy>("generateOpenApiForWynimeCloud") {
    dependsOn(generateCloudApi)
    from(layout.buildDirectory.dir("generated/openapi/src/commonMain/kotlin"))
    into("src/commonMain/gen")
}

commentFreeGeneratedSources("stripGeneratedCloudComments", file("src/commonMain/gen"), copyCloudApi)
