plugins {
    id("wynime.kmp-library")
}

kotlin {
    android {
        namespace = "com.wynime.utils.ip.parser"
    }
    sourceSets.commonMain.dependencies {
        implementation(projects.utils.logging)
    }

    sourceSets.getByName("jvmMain").dependencies {
        implementation(libs.ipaddress.parser)
    }
}
