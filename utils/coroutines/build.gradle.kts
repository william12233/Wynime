plugins {
    id("wynime.kmp-library")

}

kotlin {
    android {
        namespace = "com.wynime.utils.coroutines"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        implementation(projects.utils.platform)
        implementation(libs.atomicfu)
    }
}
