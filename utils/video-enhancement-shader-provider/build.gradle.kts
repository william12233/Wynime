import org.gradle.api.tasks.JavaExec

plugins {
    id("wynime.kmp-compose")
}

kotlin {
    android {
        namespace = "com.wynime.utils.video.enhancement.shader.provider"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.wynime.utils.video.enhancement.shader.provider"
    generateResClass = always
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = layout.buildDirectory.dir("generated/video-shader-resources"),
    )
}

val prepareVideoShaderResources = tasks.register<JavaExec>("prepareVideoShaderResources") {
    val source = layout.projectDirectory.dir("shaders")
    val destination = layout.buildDirectory.dir("generated/video-shader-resources")
    inputs.dir(source)
    inputs.file(rootProject.file("scripts/prepare-video-shader-resources.java"))
    outputs.dir(destination)
    mainClass.set(rootProject.file("scripts/prepare-video-shader-resources.java").absolutePath)
    args(source.asFile.absolutePath, destination.get().asFile.absolutePath)
}

val verifyVideoShaderResources = tasks.register<JavaExec>("verifyVideoShaderResources") {
    dependsOn(prepareVideoShaderResources)
    mainClass.set(rootProject.file("scripts/prepare-video-shader-resources.java").absolutePath)
    args(layout.projectDirectory.dir("shaders").asFile.absolutePath,
        layout.buildDirectory.dir("generated/video-shader-resources").get().asFile.absolutePath, "--check")
}

tasks.named("check") { dependsOn(verifyVideoShaderResources) }

tasks.matching {
    it.name.startsWith("convertXmlValueResources") || it.name.startsWith("copyNonXmlValueResources") ||
        it.name.startsWith("prepareComposeResourcesTask")
}.configureEach { dependsOn(prepareVideoShaderResources) }
