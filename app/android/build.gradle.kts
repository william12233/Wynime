import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    id("wynime.android-application")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.gms.google.services)
    idea
}

val archs = getPropertyOrNull("wynime.android.abis")
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
    ?.let { abis ->
        if (abis.size == 1 && abis.first() == "all") {
            listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        } else {
            abis
        }
    }
    ?: listOf("arm64-v8a")

android {
    namespace = "com.wynime.app"
    compileSdk = getIntProperty("android.compile.sdk")
    defaultConfig {
        applicationId = "com.wynime.app"
        minSdk = getIntProperty("android.min.sdk")
        targetSdk = getIntProperty("android.compile.sdk")
        versionCode = getIntProperty("android.version.code")
        versionName = project.version.toString()
        ndk {

            abiFilters.clear()

            abiFilters += archs
        }
    }
    splits {
        abi {
            isEnable = true
            reset()

            include(*archs.toTypedArray())
            isUniversalApk = true
        }
    }
    signingConfigs {
        kotlin.runCatching { getProperty("signing_release_storeFileFromRoot") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
            create("release") {
                storeFile = rootProject.file(it)
                storePassword = getProperty("signing_release_storePassword")
                keyAlias = getProperty("signing_release_keyAlias")
                keyPassword = getProperty("signing_release_keyPassword")
            }
        }
        kotlin.runCatching { getProperty("signing_release_storeFile") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
            create("release") {
                storeFile = file(it)
                storePassword = getProperty("signing_release_storePassword")
                keyAlias = getProperty("signing_release_keyAlias")
                keyPassword = getProperty("signing_release_keyPassword")
            }
        }
    }
    packaging {
        jniLibs {

            useLegacyPackaging = true
        }
        resources {
            merges.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE-notice.md")

        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                *sharedAndroidProguardRules(),
            )
        }
        debug {
            applicationIdSuffix = getLocalProperty("wynime.android.debug.applicationIdSuffix") ?: ".debug2"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("default") {
            dimension = "distribution"
        }

    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("licenses"))
}

dependencies {

    implementation(projects.app.shared)
    implementation(projects.app.shared.application)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    implementation(libs.ktor.client.core)
    implementation(libs.mediamp.ffmpeg)
}

idea {
    module {
        excludeDirs.add(file(".cxx"))
    }
}

googleServices {
    missingGoogleServicesStrategy = (getLocalProperty("wynime.enable.firebase") ?: "false").toBooleanStrict()
        .let {
            if (it) MissingGoogleServicesStrategy.ERROR else MissingGoogleServicesStrategy.IGNORE
        }
}

tasks.configureEach {
    if (name.startsWith("processTv") && name.endsWith("GoogleServices")) {
        enabled = false
    }
}

configurations.configureEach {
    if (name.startsWith("tv") && name.endsWith("Classpath")) {
        exclude(group = "dev.gitlive", module = "firebase-analytics")
        exclude(group = "dev.gitlive", module = "firebase-analytics-android")
        exclude(group = "dev.gitlive", module = "firebase-app")
        exclude(group = "dev.gitlive", module = "firebase-app-android")
        exclude(group = "com.google.firebase")
        exclude(group = "com.google.android.gms")
    }
}
