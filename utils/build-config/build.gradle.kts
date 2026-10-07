plugins {
    id("wynime.android-library")
}

android {
    val idGroup = "com.wynime"
    namespace = "$idGroup.buildconfig"
    defaultConfig {
        compileSdk = getIntProperty("android.compile.sdk")
        minSdk = getIntProperty("android.min.sdk")
    }

    buildTypes {
        release {
            buildConfigField("String", "APP_APPLICATION_ID", "\"${idGroup}\"")
        }

        debug {
            buildConfigField("String", "APP_APPLICATION_ID", "\"${idGroup}.debug2\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}