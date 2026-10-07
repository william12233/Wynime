import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
    id("wynime.kmp-library")
    idea
}

val generatedRoot = projectDir.resolve("src/commonMain/generatedKotlin")

kotlin {
    android {
        namespace = "com.wynime.utils.bbcode"
    }
    sourceSets.commonMain {
        dependencies {

            implementation(libs.antlr.kotlin.runtime)
        }
        kotlin.srcDirs(generatedRoot)
    }
}

idea {
    module {
        generatedSourceDirs.add(generatedRoot)
    }
}

val grammarGenerator = configurations.detachedConfiguration(
    dependencies.create("org.antlr:antlr4:4.13.1"),
    dependencies.create("com.strumenta:antlr-kotlin-target:${libs.versions.antlr.kotlin.get()}"),
)

val verifyGrammarComments = tasks.register<JavaExec>("verifyGrammarComments") {
    classpath = grammarGenerator
    mainClass.set(rootProject.file("scripts/strip-antlr-comments.java").absolutePath)
    args(layout.projectDirectory.file("BBCode.g4").asFile.absolutePath, "--check")
}

val generateBBCodeGrammarSource = tasks.register<JavaExec>("generateBBCodeGrammarSource") {
    dependsOn(verifyGrammarComments)
    inputs.file(layout.projectDirectory.file("BBCode.g4"))
    outputs.dir(generatedRoot)
    classpath = grammarGenerator
    mainClass.set("org.antlr.v4.Tool")
    workingDir = projectDir
    args("-Dlanguage=Kotlin", "-encoding", "UTF-8", "-visitor", "-package",
        "com.wynime.utils.bbcode", "-o", generatedRoot.absolutePath, "BBCode.g4")
}

tasks.named("check") { dependsOn(verifyGrammarComments) }

tasks.withType<KotlinCompileTool> {
    mustRunAfter(generateBBCodeGrammarSource)
}

val stripGrammarComments = commentFreeGeneratedSources("stripGeneratedGrammarComments", generatedRoot, generateBBCodeGrammarSource)
tasks.withType<KotlinCompileTool>().configureEach {
    mustRunAfter(stripGrammarComments)
}
