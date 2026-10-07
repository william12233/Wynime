@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTestCompilation
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    id("wynime.base")
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("de.mannodermaus.android-junit5")
}

configure<KotlinMultiplatformExtension> {

    jvm("desktop")

    android {
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
        androidResources.enable = true

        withHostTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }

        withDeviceTestBuilder {
            sourceSetTreeName = KotlinSourceSetTree.test.name
        }.configure {
            targetSdk {
                release(getIntProperty("android.min.sdk"))
            }
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            instrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
            instrumentationRunnerArguments["package"] = "com.wynime"
            execution = "HOST"
        }

        compilations.withType<KotlinMultiplatformAndroidDeviceTestCompilation>().configureEach {

            configurations.named(defaultSourceSet.implementationConfigurationName) {
                exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
            }
        }

        packaging {
            resources {
                pickFirsts.add("META-INF/LICENSE.md")
                pickFirsts.add("META-INF/LICENSE-notice.md")
            }
        }
    }

    applyDefaultHierarchyTemplate {
        common {
            group("jvm") {
                withJvm()
                group("android")
            }
            group("skiko") {
                withJvm()

            }
            group("mobile") {
                group("android")

            }

            group("android") {
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets.commonMain.dependencies {
        if (project.path != ":utils:platform") {
            implementation(project(":utils:platform"))
        }
    }
    sourceSets.commonTest.dependencies {
        implementation(project(":utils:testing"))
    }

    sourceSets {

        removeIf { it.name == "androidAndroidTestRelease" }
        removeIf { it.name == "androidTestFixtures" }
        removeIf { it.name == "androidTestFixturesDebug" }
        removeIf { it.name == "androidTestFixturesRelease" }
    }
}

