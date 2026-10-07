plugins {
    id("wynime.kmp-library")
}

kotlin {
    android {
        namespace = "com.wynime.utils.testing"
    }
    sourceSets.commonMain {
        dependencies {
            api(kotlin("test-annotations-common", libs.versions.kotlin.get()))
            api(libs.kotlinx.coroutines.test)
            api(projects.utils.coroutines)
        }
    }

    sourceSets.getByName("jvmMain") {
        dependencies {
            implementation(kotlin("test-junit5", libs.versions.kotlin.get()))
        }
    }
}
