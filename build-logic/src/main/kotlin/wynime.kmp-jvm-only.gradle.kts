import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("wynime.base")
    id("org.jetbrains.kotlin.multiplatform")
}

configure<KotlinMultiplatformExtension> {

    jvm()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets.commonMain.dependencies {
        implementation(project(":utils:platform"))
    }
    sourceSets.commonTest.dependencies {
        implementation(project(":utils:testing"))
    }
}

